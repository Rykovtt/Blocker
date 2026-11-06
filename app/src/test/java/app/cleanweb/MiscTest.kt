package app.cleanweb

import app.cleanweb.data.UpdateChecker
import app.cleanweb.net.Packets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    private fun syn(flags: Int): ByteArray {
        val p = ByteArray(40)
        p[0] = 0x45; p[3] = 40; p[8] = 64; p[9] = 6
        byteArrayOf(10, 0, 0, 2).copyInto(p, 12)
        byteArrayOf(8, 8, 8, 8).copyInto(p, 16)
        p[20] = 0xC3.toByte(); p[21] = 0x50          // src port 50000
        p[22] = 0x01; p[23] = 0xBB.toByte()                    // dst port 443
        p[24] = 0; p[25] = 0; p[26] = 0x10; p[27] = 0  // seq 4096
        p[32] = 0x50; p[33] = flags.toByte()
        return p
    }

    @Test fun refusesTcpSynWithReset() {
        val s = Packets.parseTcpSyn(syn(0x02), 40)
        assertNotNull(s)
        assertEquals(443, s!!.dstPort)
        val rst = Packets.buildRst(s)
        assertEquals(40, rst.size)
        assertEquals(0, Packets.checksum(rst, 0, 20, 0))               // valid IP header
        assertEquals(0x14, rst[33].toInt())                           // RST+ACK
        assertEquals(0x1001, ((rst[30].toInt() and 0xFF) shl 8) or (rst[31].toInt() and 0xFF)) // ack = seq + 1
        assertTrue(rst.copyOfRange(12, 16).contentEquals(byteArrayOf(8, 8, 8, 8)))
    }

    @Test fun ignoresNonSyn() {
        assertNull(Packets.parseTcpSyn(syn(0x12), 40)) // SYN+ACK
        assertNull(Packets.parseTcpSyn(syn(0x10), 40)) // ACK
    }

    @Test fun comparesVersions() {
        assertTrue(UpdateChecker.compare("1.2.1", "1.2.0") > 0)
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.9") > 0)
        assertEquals(0, UpdateChecker.compare("1.2", "1.2.0"))
        assertTrue(UpdateChecker.compare("1.1.9", "1.2.0") < 0)
    }
}
