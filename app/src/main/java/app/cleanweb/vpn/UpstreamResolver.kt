package app.cleanweb.vpn

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import app.cleanweb.data.DnsProtocol
import app.cleanweb.data.SettingsState
import app.cleanweb.data.Upstream
import app.cleanweb.dns.Dns
import app.cleanweb.dns.DnsQuery
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Sends allowed queries to the chosen upstream: DNS-over-HTTPS first,
 * plain UDP to the provider's IP as a fallback. Answers are cached by their TTL.
 *
 * The app itself is excluded from the VPN, so these sockets use the normal network.
 */
private val GOOGLE = listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)))

class UpstreamResolver(private val vpn: VpnService, settings: SettingsState) {
    private val dohUrl: URL?
    private val dot: DotClient?
    private val udpServer: InetAddress

    init {
        val custom = settings.customUpstream
        var dotClient: DotClient? = null
        when {
            settings.upstream != Upstream.CUSTOM && settings.protocol == DnsProtocol.DOT -> {
                dohUrl = null
                dotClient = DotClient(settings.upstream.dotHost, 853)
                udpServer = InetAddress.getByName(settings.upstream.plainIp)
            }
            settings.upstream != Upstream.CUSTOM -> {
                dohUrl = URL(settings.upstream.dohUrl)
                udpServer = InetAddress.getByName(settings.upstream.plainIp)
            }
            custom.startsWith("tls://") -> {
                val hp = custom.removePrefix("tls://").trimEnd('/')
                val host = hp.substringBeforeLast(':', hp).ifBlank { "one.one.one.one" }
                val port = hp.substringAfterLast(':', "").toIntOrNull() ?: 853
                dohUrl = null
                dotClient = DotClient(host, port)
                udpServer = InetAddress.getByName(Upstream.CLOUDFLARE.plainIp)
            }
            custom.startsWith("https://") -> {
                dohUrl = URL(custom)
                udpServer = InetAddress.getByName(Upstream.CLOUDFLARE.plainIp)
            }
            else -> {
                dohUrl = null
                udpServer = runCatching { InetAddress.getByName(custom.ifBlank { "1.1.1.1" }) }
                    .getOrElse { InetAddress.getByName("1.1.1.1") }
            }
        }
        dot = dotClient
    }

    private class Cached(val data: ByteArray, val expires: Long)

    private val cache = object : LinkedHashMap<String, Cached>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > 4000
    }

    fun resolve(query: ByteArray, q: DnsQuery): ByteArray? {
        val key = "${q.qtype}|${q.name}"
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && hit.expires > now) return Dns.withId(hit.data, q.id)
        }
        val resp = encrypted(query) ?: plainFallback(query) ?: return null
        val rcode = Dns.rcode(resp)
        if (rcode == 0 || rcode == 3) {
            val ttl = (Dns.minTtl(resp) ?: 60).coerceIn(10, 3600)
            synchronized(cache) { cache[key] = Cached(resp, now + ttl * 1000L) }
        }
        return Dns.withId(resp, q.id)
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    // ---- failover ----
    // Some networks block DoH/DoT. After 3 failures in a row the encrypted server is skipped
    // for a minute, and queries go to plain DNS so the internet keeps working.
    @Volatile private var encryptedFailures = 0
    @Volatile private var encryptedSkipUntil = 0L

    private fun encrypted(query: ByteArray): ByteArray? {
        if (dot == null && dohUrl == null) return null
        if (System.currentTimeMillis() < encryptedSkipUntil) return null
        val r = (dot?.let { runCatching { it.exchange(query) }.getOrNull() })
            ?: (dohUrl?.let { runCatching { doh(it, query) }.getOrNull() })
        if (r != null) {
            encryptedFailures = 0
        } else if (++encryptedFailures >= 3) {
            encryptedSkipUntil = System.currentTimeMillis() + 60_000
            encryptedFailures = 0
        }
        return r
    }

    /** Provider over plain DNS, then the network's own DNS servers, then Google. */
    private fun plainFallback(query: ByteArray): ByteArray? {
        for (server in fallbackServers()) {
            runCatching { return udp(query, server) }
        }
        return null
    }

    @Volatile private var systemDns: List<InetAddress> = emptyList()
    @Volatile private var systemDnsAt = 0L

    private fun fallbackServers(): List<InetAddress> {
        val now = System.currentTimeMillis()
        if (now - systemDnsAt > 30_000) {
            systemDns = runCatching {
                val cm = vpn.getSystemService(ConnectivityManager::class.java)
                @Suppress("DEPRECATION")
                cm.allNetworks.filter { n ->
                    cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
                }.flatMap { n -> cm.getLinkProperties(n)?.dnsServers.orEmpty() }
            }.getOrDefault(emptyList())
            systemDnsAt = now
        }
        return (listOf(udpServer) + systemDns + GOOGLE).distinct()
    }

    fun close() = dot?.close()

    private fun doh(url: URL, query: ByteArray): ByteArray {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setFixedLengthStreamingMode(query.size)
            conn.outputStream.use { it.write(query) }
            if (conn.responseCode != 200) error("DoH HTTP ${conn.responseCode}")
            return conn.inputStream.use { input ->
                val out = ByteArrayOutputStream(512)
                val buf = ByteArray(4096)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 65535) error("DoH answer too large")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun udp(query: ByteArray, server: InetAddress): ByteArray {
        DatagramSocket().use { socket ->
            vpn.protect(socket)
            socket.soTimeout = 2000
            socket.send(DatagramPacket(query, query.size, server, 53))
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            while (true) {
                socket.receive(packet)
                val resp = buf.copyOf(packet.length)
                if (Dns.id(resp) == Dns.id(query)) return resp
            }
        }
    }
}
