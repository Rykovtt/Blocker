package app.cleanweb.vpn

import android.net.VpnService
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
class UpstreamResolver(private val vpn: VpnService, settings: SettingsState) {
    private val dohUrl: URL?
    private val udpServer: InetAddress

    init {
        val custom = settings.customUpstream
        when {
            settings.upstream != Upstream.CUSTOM -> {
                dohUrl = URL(settings.upstream.dohUrl)
                udpServer = InetAddress.getByName(settings.upstream.plainIp)
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
        val resp = (dohUrl?.let { runCatching { doh(it, query) }.getOrNull() })
            ?: runCatching { udp(query) }.getOrNull()
            ?: return null
        val rcode = Dns.rcode(resp)
        if (rcode == 0 || rcode == 3) {
            val ttl = (Dns.minTtl(resp) ?: 60).coerceIn(10, 3600)
            synchronized(cache) { cache[key] = Cached(resp, now + ttl * 1000L) }
        }
        return Dns.withId(resp, q.id)
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

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

    private fun udp(query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            vpn.protect(socket)
            socket.soTimeout = 3000
            socket.send(DatagramPacket(query, query.size, udpServer, 53))
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
