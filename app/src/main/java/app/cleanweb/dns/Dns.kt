package app.cleanweb.dns

import app.cleanweb.net.put16
import app.cleanweb.net.put32
import app.cleanweb.net.u16
import app.cleanweb.net.u32

class DnsQuery(val id: Int, val name: String, val qtype: Int, val questionEnd: Int)

/** Just enough DNS wire format to read a question and forge answers. */
object Dns {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val RCODE_SERVFAIL = 2
    const val BLOCK_TTL = 60

    fun parseQuery(m: ByteArray): DnsQuery? {
        if (m.size < 17) return null
        val flags = u16(m, 2)
        if ((flags and 0x8000) != 0) return null // a response, not a query
        if (((flags shr 11) and 0xF) != 0) return null // only standard QUERY
        if (u16(m, 4) < 1) return null
        val (name, end) = readName(m, 12) ?: return null
        if (end + 4 > m.size) return null
        return DnsQuery(u16(m, 0), name.lowercase(), u16(m, end), end + 4)
    }

    /** Reads a (possibly compressed) name. Returns the name and the offset after it. */
    fun readName(m: ByteArray, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var pos = start
        var end = -1
        var jumps = 0
        while (true) {
            if (pos >= m.size) return null
            val len = m[pos].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (end < 0) end = pos + 1
                    return sb.toString() to end
                }
                (len and 0xC0) == 0xC0 -> {
                    if (pos + 1 >= m.size) return null
                    if (end < 0) end = pos + 2
                    pos = ((len and 0x3F) shl 8) or (m[pos + 1].toInt() and 0xFF)
                    if (++jumps > 32) return null
                }
                (len and 0xC0) != 0 -> return null
                else -> {
                    if (pos + 1 + len > m.size) return null
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in 1..len) sb.append((m[pos + i].toInt() and 0xFF).toChar())
                    if (sb.length > 255) return null
                    pos += len + 1
                }
            }
        }
    }

    /** Answers A with 0.0.0.0 and AAAA with ::, other types with an empty NOERROR. */
    fun blockedResponse(query: ByteArray, q: DnsQuery): ByteArray {
        val qLen = q.questionEnd - 12
        val addrLen = when (q.qtype) {
            TYPE_A -> 4
            TYPE_AAAA -> 16
            else -> 0
        }
        val out = ByteArray(12 + qLen + if (addrLen > 0) 12 + addrLen else 0)
        writeHeader(out, query, q, rcode = 0, answers = if (addrLen > 0) 1 else 0)
        if (addrLen > 0) {
            val p = 12 + qLen
            put16(out, p, 0xC00C) // pointer to the question name
            put16(out, p + 2, q.qtype)
            put16(out, p + 4, 1) // class IN
            put32(out, p + 6, BLOCK_TTL)
            put16(out, p + 10, addrLen) // address bytes stay zero
        }
        return out
    }

    fun errorResponse(query: ByteArray, q: DnsQuery, rcode: Int = RCODE_SERVFAIL): ByteArray {
        val out = ByteArray(q.questionEnd)
        writeHeader(out, query, q, rcode, answers = 0)
        return out
    }

    private fun writeHeader(out: ByteArray, query: ByteArray, q: DnsQuery, rcode: Int, answers: Int) {
        put16(out, 0, q.id)
        // QR=1, keep RD from the query, RA=1
        put16(out, 2, 0x8000 or (u16(query, 2) and 0x0100) or 0x0080 or (rcode and 0xF))
        put16(out, 4, 1)
        put16(out, 6, answers)
        query.copyInto(out, 12, 12, q.questionEnd)
    }

    fun rcode(resp: ByteArray): Int = if (resp.size < 4) -1 else u16(resp, 2) and 0xF

    fun withId(m: ByteArray, id: Int): ByteArray = m.copyOf().also { put16(it, 0, id) }

    fun id(m: ByteArray): Int = if (m.size < 2) -1 else u16(m, 0)

    /** Smallest TTL among answer records, or null if there are none or the message is malformed. */
    fun minTtl(resp: ByteArray): Int? {
        if (resp.size < 12) return null
        val qd = u16(resp, 4)
        val an = u16(resp, 6)
        if (an == 0) return null
        var pos = 12
        repeat(qd) {
            val (_, end) = readName(resp, pos) ?: return null
            pos = end + 4
        }
        var min = Long.MAX_VALUE
        repeat(an) {
            val (_, end) = readName(resp, pos) ?: return null
            if (end + 10 > resp.size) return null
            val ttl = u32(resp, end + 4)
            val rdLen = u16(resp, end + 8)
            if (ttl < min) min = ttl
            pos = end + 10 + rdLen
        }
        return min.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
