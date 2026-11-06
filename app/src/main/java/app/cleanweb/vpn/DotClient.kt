package app.cleanweb.vpn

import app.cleanweb.dns.Dns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * DNS-over-TLS (RFC 7858). Keeps a few TLS connections open and reuses them;
 * each query is a 2-byte length prefix + the DNS message.
 */
class DotClient(private val host: String, private val port: Int) {
    private class Conn(val socket: SSLSocket) {
        val input = DataInputStream(socket.inputStream.buffered())
        val output = DataOutputStream(socket.outputStream.buffered())
    }

    private val idle = ArrayDeque<Conn>()

    fun exchange(query: ByteArray): ByteArray {
        // A reused connection may have been closed by the server: retry once on a fresh one.
        val reused = take()
        if (reused != null) {
            try {
                return send(reused, query).also { give(reused) }
            } catch (_: IOException) {
                runCatching { reused.socket.close() }
            }
        }
        val fresh = open()
        try {
            return send(fresh, query).also { give(fresh) }
        } catch (e: IOException) {
            runCatching { fresh.socket.close() }
            throw e
        }
    }

    private fun send(c: Conn, query: ByteArray): ByteArray {
        c.output.writeShort(query.size)
        c.output.write(query)
        c.output.flush()
        while (true) {
            val len = c.input.readUnsignedShort()
            val resp = ByteArray(len)
            c.input.readFully(resp)
            if (Dns.id(resp) == Dns.id(query)) return resp
        }
    }

    private fun open(): Conn {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 4000)
        s.soTimeout = 4000
        val wrapped = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(s, host, port, true) as SSLSocket
        wrapped.soTimeout = 4000
        wrapped.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, wrapped.session)) {
            wrapped.close()
            throw IOException("DoT certificate does not match $host")
        }
        return Conn(wrapped)
    }

    private fun take(): Conn? = synchronized(idle) { idle.removeFirstOrNull() }

    private fun give(c: Conn) = synchronized(idle) {
        if (idle.size < 4) idle.addLast(c) else runCatching { c.socket.close() }
    }

    fun close() = synchronized(idle) {
        idle.forEach { runCatching { it.socket.close() } }
        idle.clear()
    }
}
