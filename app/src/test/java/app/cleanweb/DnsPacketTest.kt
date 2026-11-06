package app.cleanweb

import app.cleanweb.dns.Dns
import app.cleanweb.net.Packets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsPacketTest {
    private fun query(name: String, type: Int, id: Int = 0x1234): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.split('.')) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(0)
        out.write(byteArrayOf(0, type.toByte(), 0, 1))
        return out.toByteArray()
    }

    @Test fun parsesQuery() {
        val q = Dns.parseQuery(query("Ads.Example.com", Dns.TYPE_A))!!
        assertEquals("ads.example.com", q.name)
        assertEquals(Dns.TYPE_A, q.qtype)
        assertEquals(0x1234, q.id)
    }

    @Test fun blockedAnswerIsZeroAddress() {
        val raw = query("ads.example.com", Dns.TYPE_A)
        val q = Dns.parseQuery(raw)!!
        val resp = Dns.blockedResponse(raw, q)
        assertEquals(0, Dns.rcode(resp))
        assertEquals(Dns.BLOCK_TTL, Dns.minTtl(resp))
        assertArrayEquals(ByteArray(4), resp.copyOfRange(resp.size - 4, resp.size))
        assertEquals(0x1234, Dns.id(resp))
        assertEquals(0x81, resp[2].toInt() and 0xFF) // QR + RD
    }

    @Test fun blockedAaaaAndOtherTypes() {
        val raw6 = query("ads.example.com", Dns.TYPE_AAAA)
        val r6 = Dns.blockedResponse(raw6, Dns.parseQuery(raw6)!!)
        assertEquals(raw6.size + 12 + 16, r6.size)
        val rawHttps = query("ads.example.com", 65)
        val rh = Dns.blockedResponse(rawHttps, Dns.parseQuery(rawHttps)!!)
        assertEquals(rawHttps.size, rh.size)
        assertEquals(null, Dns.minTtl(rh))
    }

    @Test fun ipv4UdpRoundTrip() {
        val payload = query("example.com", Dns.TYPE_A)
        val pkt = Packets.buildUdp(4, byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 40000, 53, payload)
        assertEquals(0, Packets.checksum(pkt, 0, 20, 0)) // valid header checksum
        val p = Packets.parseUdp(pkt, pkt.size)
        assertNotNull(p)
        assertEquals(53, p!!.dstPort)
        assertEquals(40000, p.srcPort)
        assertArrayEquals(payload, p.payload)
    }

    @Test fun ipv6UdpRoundTrip() {
        val payload = query("example.com", Dns.TYPE_AAAA)
        val src = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 2 }
        val dst = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 }
        val pkt = Packets.buildUdp(6, src, dst, 5353, 53, payload)
        val p = Packets.parseUdp(pkt, pkt.size)!!
        assertEquals(6, p.ipVersion)
        assertArrayEquals(dst, p.dstAddr)
        assertArrayEquals(payload, p.payload)
    }
}
