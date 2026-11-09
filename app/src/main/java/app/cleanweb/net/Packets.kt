package app.cleanweb.net

/** A UDP datagram read from the tunnel, with its IP addresses. */
class UdpPacket(
    val ipVersion: Int,
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

/** Minimal IPv4/IPv6 + UDP parsing and building. Only what DNS needs. */
object Packets {
    private const val PROTO_UDP = 17

    fun parseUdp(buf: ByteArray, len: Int): UdpPacket? {
        if (len < 1) return null
        return when ((buf[0].toInt() shr 4) and 0xF) {
            4 -> parseV4(buf, len)
            6 -> parseV6(buf, len)
            else -> null
        }
    }

    private fun parseV4(b: ByteArray, len: Int): UdpPacket? {
        if (len < 28) return null
        val ihl = (b[0].toInt() and 0xF) * 4
        if (ihl < 20) return null
        val total = u16(b, 2)
        if (total > len || total < ihl + 8) return null
        if ((b[9].toInt() and 0xFF) != PROTO_UDP) return null
        // Skip fragments: DNS queries are never fragmented in practice.
        if ((u16(b, 6) and 0x3FFF) != 0) return null
        val udpLen = u16(b, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > total) return null
        return UdpPacket(
            4, b.copyOfRange(12, 16), b.copyOfRange(16, 20),
            u16(b, ihl), u16(b, ihl + 2), b.copyOfRange(ihl + 8, ihl + udpLen),
        )
    }

    private fun parseV6(b: ByteArray, len: Int): UdpPacket? {
        if (len < 48) return null
        if ((b[6].toInt() and 0xFF) != PROTO_UDP) return null // no extension headers
        val payloadLen = u16(b, 4)
        if (40 + payloadLen > len) return null
        val udpLen = u16(b, 44)
        if (udpLen < 8 || udpLen > payloadLen) return null
        return UdpPacket(
            6, b.copyOfRange(8, 24), b.copyOfRange(24, 40),
            u16(b, 40), u16(b, 42), b.copyOfRange(48, 40 + udpLen),
        )
    }

    fun buildUdp(
        ipVersion: Int,
        src: ByteArray,
        dst: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipHeader = if (ipVersion == 4) 20 else 40
        val out = ByteArray(ipHeader + udpLen)
        if (ipVersion == 4) {
            out[0] = 0x45
            put16(out, 2, out.size)
            put16(out, 6, 0x4000) // don't fragment
            out[8] = 64
            out[9] = PROTO_UDP.toByte()
            src.copyInto(out, 12)
            dst.copyInto(out, 16)
            put16(out, 10, checksum(out, 0, 20, 0))
        } else {
            out[0] = 0x60
            put16(out, 4, udpLen)
            out[6] = PROTO_UDP.toByte()
            out[7] = 64
            src.copyInto(out, 8)
            dst.copyInto(out, 24)
        }
        val u = ipHeader
        put16(out, u, srcPort)
        put16(out, u + 2, dstPort)
        put16(out, u + 4, udpLen)
        payload.copyInto(out, u + 8)
        val pseudo = wordSum(src) + wordSum(dst) + PROTO_UDP + udpLen
        val cs = checksum(out, u, udpLen, pseudo)
        put16(out, u + 6, if (cs == 0) 0xFFFF else cs)
        return out
    }

    fun checksum(b: ByteArray, off: Int, len: Int, initial: Long): Int {
        var sum = initial
        var i = off
        val end = off + len
        while (i + 1 < end) {
            sum += u16(b, i)
            i += 2
        }
        if (i < end) sum += (b[i].toInt() and 0xFF) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun wordSum(a: ByteArray): Long {
        var s = 0L
        var i = 0
        while (i + 1 < a.size) {
            s += u16(a, i)
            i += 2
        }
        return s
    }
}
