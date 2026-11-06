package app.cleanweb

import app.cleanweb.filter.FilterEngine
import app.cleanweb.filter.RuleSet
import app.cleanweb.filter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private fun engine(vararg lines: String) = FilterEngine(RuleSet().apply { lines.forEach { addLine(it) } })

    @Test fun adblockDomainRuleBlocksSubdomains() {
        val e = engine("||ads.example.com^")
        assertEquals(Verdict.BLOCKED, e.check("ads.example.com"))
        assertEquals(Verdict.BLOCKED, e.check("x.ads.example.com"))
        assertEquals(Verdict.NONE, e.check("example.com"))
        assertEquals(Verdict.NONE, e.check("bads.example.com"))
    }

    @Test fun hostsRuleIsExact() {
        val e = engine("0.0.0.0 tracker.net", "127.0.0.1 localhost", "::1 ip6-localhost")
        assertEquals(Verdict.BLOCKED, e.check("tracker.net"))
        assertEquals(Verdict.NONE, e.check("sub.tracker.net"))
        assertEquals(Verdict.NONE, e.check("localhost"))
    }

    @Test fun exceptionWins() {
        val e = engine("||example.com^", "@@||good.example.com^")
        assertEquals(Verdict.BLOCKED, e.check("bad.example.com"))
        assertEquals(Verdict.ALLOWED, e.check("good.example.com"))
        assertEquals(Verdict.ALLOWED, e.check("a.good.example.com"))
    }

    @Test fun skipsRulesDnsCannotApply() {
        val rs = RuleSet()
        assertFalse(rs.addLine("! comment"))
        assertFalse(rs.addLine("[Adblock Plus 2.0]"))
        assertFalse(rs.addLine("example.com##.banner"))
        assertFalse(rs.addLine("||example.com/ads/*"))
        assertFalse(rs.addLine("||example.com^\$script"))
        assertFalse(rs.addLine("||example.com^\$domain=foo.com"))
        assertFalse(rs.addLine("/banner[0-9]+/"))
        assertFalse(rs.addLine("|https://x.com/"))
        assertTrue(rs.addLine("||adnet.com^\$third-party"))
        assertTrue(rs.addLine("||adnet2.com^|"))
        assertTrue(rs.addLine("plain-domain.org"))
        assertEquals(3, rs.size)
    }

    @Test fun normalizesDomains() {
        assertEquals("example.com", RuleSet.normalizeDomain("Example.COM."))
        assertEquals(null, RuleSet.normalizeDomain("1.2.3.4"))
        assertEquals(null, RuleSet.normalizeDomain("nodot"))
        assertEquals(null, RuleSet.normalizeDomain("bad..com"))
    }

    /** Optional: parse real downloaded lists, e.g. -Dlists=/path/a.txt,/path/b.txt */
    @Test fun parsesRealListsIfGiven() {
        val paths = System.getProperty("lists")?.takeIf { it.isNotBlank() }?.split(",") ?: return
        val rs = RuleSet()
        val start = System.nanoTime()
        for (p in paths) {
            val n = java.io.File(p).bufferedReader().useLines { rs.addAll(it) }
            println("$p -> $n rules")
        }
        println("total ${rs.size} unique in ${(System.nanoTime() - start) / 1_000_000} ms")
        val e = FilterEngine(rs)
        val t = System.nanoTime()
        repeat(100_000) { e.check("sub$it.doubleclick.net") }
        println("lookup avg ${(System.nanoTime() - t) / 100_000} ns")
        assertEquals(Verdict.BLOCKED, e.check("doubleclick.net"))
    }
}
