package app.cleanweb.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.content.ContextCompat
import app.cleanweb.AppGraph
import app.cleanweb.R
import app.cleanweb.data.LogEntry
import app.cleanweb.data.QueryLog
import app.cleanweb.data.QueryStatus
import app.cleanweb.dns.Dns
import app.cleanweb.filter.FilterEngine
import app.cleanweb.filter.Verdict
import app.cleanweb.net.Packets
import app.cleanweb.net.UdpPacket
import app.cleanweb.system.Notifications
import app.cleanweb.system.ProtectionTileService
import app.cleanweb.widget.ProtectionWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.system.ErrnoException
import android.system.Os
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local VPN that only captures DNS. The system is told to use a fake DNS server
 * (10.111.222.1 / fd66:6c74:7765::1) that is routed into the tunnel; nothing else is.
 * Each query is checked against the filters: blocked names get 0.0.0.0 / ::,
 * the rest are resolved through the encrypted upstream.
 */
class BlockerVpnService : VpnService() {
    private val graph by lazy { AppGraph.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())

    private var tun: ParcelFileDescriptor? = null
    private var current: ReaderHandle? = null
    private var jobs = mutableListOf<Job>()

    @Volatile private var engine: FilterEngine = FilterEngine.EMPTY
    @Volatile private var resolver: UpstreamResolver? = null
    @Volatile private var paused = false
    @Volatile private var unfilteredUids: Set<Int> = emptySet()

    private val appNames = ConcurrentHashMap<Int, String>()
    private val resumeRunnable = Runnable { resume() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                graph.settings.setProtectionWanted(false)
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> pause(intent.getLongExtra(EXTRA_MINUTES, 5))
            ACTION_RESUME -> resume()
            ACTION_RESTART -> if (tun != null) establish() else startProtection()
            else -> startProtection() // ACTION_START, or a null intent from always-on VPN / restart
        }
        return START_STICKY
    }

    private fun startProtection() {
        goForeground()
        graph.settings.setProtectionWanted(true)
        BlockerState.lastError.value = null
        if (tun != null) return
        BlockerState.status.value = ProtectionStatus.STARTING
        if (!establish()) return
        BlockerState.status.value = ProtectionStatus.RUNNING
        observe()
        requestTileUpdate()
    }

    private fun goForeground() {
        val n = Notifications.protection(this, BlockerState.status.value, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(Notifications.ID_PROTECTION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } catch (e: Exception) {
                Log.w(TAG, "systemExempted refused, using specialUse", e)
                startForeground(Notifications.ID_PROTECTION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            startForeground(Notifications.ID_PROTECTION, n)
        }
    }

    /** (Re)creates the tunnel. Called on start and when the excluded-apps list changes. */
    private fun establish(): Boolean {
        val settings = graph.settings.current
        resolver?.close()
        resolver = UpstreamResolver(this, settings)
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .setBlocking(false)
            .addAddress(TUN_V4, 32)
            .addRoute(DNS_V4, 32)
            .addDnsServer(DNS_V4)
            .addAddress(TUN_V6, 128)
            .addRoute(DNS_V6, 128)
            .addDnsServer(DNS_V6)
        // Only our own DNS address is routed into the tunnel. Public resolvers such as 8.8.8.8
        // are NOT captured: many apps ping them to check for internet and would think they are offline.
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
        // Our own traffic (DoH, list downloads) must not loop through the tunnel.
        builder.addDisallowedApplication(packageName)
        // With Android's "Block connections without VPN" on, apps left outside the VPN get no
        // internet at all. Then excluded apps stay inside and their DNS is simply not filtered.
        val lockdown = Build.VERSION.SDK_INT >= 29 && runCatching { isLockdownEnabled }.getOrDefault(false)
        val uids = HashSet<Int>()
        for (pkg in settings.excludedApps) {
            try {
                uids.add(packageManager.getPackageUid(pkg, 0))
                if (!lockdown) builder.addDisallowedApplication(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // not installed
            }
        }
        unfilteredUids = if (lockdown) uids else emptySet()
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            builder.setConfigureIntent(Notifications.openAppIntent(this))
        }

        val newTun = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
            null
        }
        if (newTun == null) {
            BlockerState.lastError.value = R.string.error_vpn_not_permitted
            shutdown()
            return false
        }
        stopReader()
        tun = newTun
        startReader(newTun)
        return true
    }

    private fun observe() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        // Rebuild the engine whenever lists, user rules or the whitelist change.
        jobs += scope.launch {
            graph.filters.version.collectLatest {
                BlockerState.engineLoading.value = true
                val e = runCatching { graph.filters.buildEngine() }.getOrNull()
                if (e != null) {
                    engine = e
                    BlockerState.engine = e
                    BlockerState.ruleCount.value = e.ruleCount
                }
                resolver?.clearCache()
                BlockerState.engineLoading.value = false
            }
        }
        // Excluded apps or upstream changed: rebuild the tunnel.
        jobs += scope.launch {
            graph.settings.state
                .map { listOf(it.excludedApps, it.upstream, it.customUpstream, it.protocol) }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { main.post { if (tun != null) establish() } }
        }
        // Keep the notification counter fresh without redrawing on every query.
        jobs += scope.launch {
            var shown = -1 to ProtectionStatus.STOPPED
            while (true) {
                val s = BlockerState.status.value
                if (s != ProtectionStatus.STOPPED) {
                    val today = graph.log.countsSince(QueryLog.startOfToday()).blocked
                    if (shown != today to s) {
                        Notifications.update(this@BlockerVpnService, s, today)
                        ProtectionWidget.render(this@BlockerVpnService, s, today)
                        shown = today to s
                    }
                }
                delay(5000)
            }
        }
    }

    /**
     * Reads packets from the tunnel. Non-blocking + poll with a timeout, so the thread
     * notices [running] going false and closes its own descriptor when replaced or stopped.
     */
    private fun startReader(pfd: ParcelFileDescriptor) {
        val workers = ThreadPoolExecutor(2, 16, 30, TimeUnit.SECONDS, LinkedBlockingQueue(2048), ThreadPoolExecutor.DiscardOldestPolicy())
        val running = AtomicBoolean(true)
        val fd = pfd.fileDescriptor
        val tunnel = Tunnel(fd)
        current = ReaderHandle(running, workers)
        Thread({
            val buf = ByteArray(32767)
            val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
            try {
                while (running.get()) {
                    poll.revents = 0
                    if (Os.poll(arrayOf(poll), 1000) == 0) continue
                    val rev = poll.revents.toInt()
                    if (rev and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) break
                    val n = try {
                        Os.read(fd, buf, 0, buf.size)
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue else break
                    }
                    if (n <= 0) continue
                    val pkt = Packets.parseUdp(buf, n) ?: continue
                    if (pkt.dstPort != 53) continue
                    try {
                        workers.execute { handle(pkt, tunnel) }
                    } catch (_: RejectedExecutionException) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "reader stopped", e)
            } finally {
                workers.shutdownNow()
                try { pfd.close() } catch (_: IOException) {}
            }
        }, "tun-reader").start()
    }

    private class ReaderHandle(val running: AtomicBoolean, val workers: ExecutorService)

    /** Writes whole packets to the tunnel, retrying briefly if it is momentarily full. */
    private class Tunnel(private val fd: FileDescriptor) {
        fun write(packet: ByteArray) = synchronized(this) {
            var tries = 0
            while (true) {
                try {
                    Os.write(fd, packet, 0, packet.size)
                    return@synchronized
                } catch (e: ErrnoException) {
                    if (e.errno != OsConstants.EAGAIN || ++tries > 20) throw IOException(e)
                    Thread.sleep(2)
                }
            }
        }
    }

    /** Signals the current reader to stop; it closes the old tunnel itself within a second. */
    private fun stopReader() {
        current?.running?.set(false)
        current = null
        tun = null
    }

    private fun handle(pkt: UdpPacket, tunnel: Tunnel) {
        val q = Dns.parseQuery(pkt.payload) ?: return
        val uid = uidFor(pkt)
        val verdict = when {
            paused -> Verdict.NONE
            uid != null && uid in unfilteredUids -> Verdict.NONE // excluded app under VPN lockdown
            else -> engine.check(q.name)
        }
        val response: ByteArray
        val status: QueryStatus
        if (q.name == FIREFOX_DOH_CANARY) {
            response = Dns.errorResponse(pkt.payload, q, rcode = 3) // NXDOMAIN: Firefox then leaves DoH off
            status = QueryStatus.BLOCKED
        } else if (verdict == Verdict.BLOCKED) {
            response = Dns.blockedResponse(pkt.payload, q)
            status = QueryStatus.BLOCKED
        } else {
            val r = resolver?.resolve(pkt.payload, q)
            if (r == null) {
                response = Dns.errorResponse(pkt.payload, q)
                status = QueryStatus.FAILED
            } else {
                response = r
                status = if (verdict == Verdict.ALLOWED) QueryStatus.WHITELISTED else QueryStatus.ALLOWED
            }
        }
        val out = Packets.buildUdp(pkt.ipVersion, pkt.dstAddr, pkt.srcAddr, pkt.dstPort, pkt.srcPort, response)
        try {
            tunnel.write(out)
        } catch (_: IOException) {
            return
        }
        if (status == QueryStatus.BLOCKED) BlockerState.blockedSinceStart.value++
        if (graph.settings.current.logEnabled) {
            val app = uid?.let { u -> appNames.getOrPut(u) { labelForUid(u) } }
            graph.log.record(LogEntry(System.currentTimeMillis(), q.name, q.qtype, app, status))
        }
    }

    /** Which app (uid) sent the query (Android 10+). */
    @SuppressLint("NewApi")
    private fun uidFor(pkt: UdpPacket): Int? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(pkt.srcAddr), pkt.srcPort),
                InetSocketAddress(InetAddress.getByAddress(pkt.dstAddr), pkt.dstPort),
            ).takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun labelForUid(uid: Int): String {
        if (uid < 10000) return getString(R.string.log_app_system)
        val pkgs = packageManager.getPackagesForUid(uid) ?: return "uid $uid"
        val pkg = pkgs.first()
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun pause(minutes: Long) {
        if (tun == null) return
        paused = true
        val until = System.currentTimeMillis() + minutes * 60_000
        BlockerState.pausedUntil.value = until
        BlockerState.status.value = ProtectionStatus.PAUSED
        resolver?.clearCache()
        main.removeCallbacks(resumeRunnable)
        main.postDelayed(resumeRunnable, minutes * 60_000)
        requestTileUpdate()
    }

    private fun resume() {
        main.removeCallbacks(resumeRunnable)
        paused = false
        BlockerState.pausedUntil.value = 0
        if (tun != null) BlockerState.status.value = ProtectionStatus.RUNNING
        requestTileUpdate()
    }

    override fun onRevoke() {
        // Another VPN app took over, or the user turned us off in system settings.
        BlockerState.lastError.value = R.string.error_vpn_revoked
        graph.settings.setProtectionWanted(false)
        Notifications.showRevoked(this)
        shutdown()
    }

    private fun shutdown() {
        main.removeCallbacks(resumeRunnable)
        paused = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        stopReader()
        engine = FilterEngine.EMPTY
        BlockerState.status.value = ProtectionStatus.STOPPED
        BlockerState.pausedUntil.value = 0
        BlockerState.blockedSinceStart.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        requestTileUpdate()
    }

    override fun onDestroy() {
        if (tun != null) shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun requestTileUpdate() {
        ProtectionTileService.requestUpdate(this)
        if (BlockerState.status.value == ProtectionStatus.STOPPED) {
            val app = applicationContext
            Thread { runCatching { ProtectionWidget.render(app, ProtectionStatus.STOPPED, graph.log.countsSince(QueryLog.startOfToday()).blocked) } }.start()
        }
    }

    companion object {
        private const val TAG = "CleanWebVpn"
        private const val TUN_V4 = "10.111.222.2"
        private const val DNS_V4 = "10.111.222.1"
        private const val TUN_V6 = "fd66:6c74:7765::2"
        private const val DNS_V6 = "fd66:6c74:7765::1"

        private const val FIREFOX_DOH_CANARY = "use-application-dns.net"

        const val ACTION_START = "app.cleanweb.action.START"
        const val ACTION_STOP = "app.cleanweb.action.STOP"
        const val ACTION_PAUSE = "app.cleanweb.action.PAUSE"
        const val ACTION_RESUME = "app.cleanweb.action.RESUME"
        const val ACTION_RESTART = "app.cleanweb.action.RESTART"
        const val EXTRA_MINUTES = "minutes"

        fun intent(context: Context, action: String) =
            Intent(context, BlockerVpnService::class.java).setAction(action)

        /** Starts protection. The caller must have VPN permission (VpnService.prepare == null). */
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))

        fun stop(context: Context) {
            if (BlockerState.status.value == ProtectionStatus.STOPPED) return
            context.startService(intent(context, ACTION_STOP))
        }

        fun pause(context: Context, minutes: Long = 5) {
            if (BlockerState.status.value == ProtectionStatus.STOPPED) return
            context.startService(intent(context, ACTION_PAUSE).putExtra(EXTRA_MINUTES, minutes))
        }

        fun resume(context: Context) {
            if (BlockerState.status.value == ProtectionStatus.STOPPED) return
            context.startService(intent(context, ACTION_RESUME))
        }
    }
}
