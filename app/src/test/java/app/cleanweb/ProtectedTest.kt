package app.cleanweb

import app.cleanweb.data.FilterRepository
import app.cleanweb.filter.FilterEngine
import app.cleanweb.filter.RuleSet
import app.cleanweb.filter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtectedTest {
    private val protected = File("src/main/assets/protected_allowlist.txt").readLines()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("!") }

    @Test fun banksStayReachableEvenIfAListBlocksThem() {
        val rules = RuleSet().apply {
            addLine("||partner.privatbank.ua^\$third-party") // from RU AdList
            addLine("||privat24.ua^")                         // e.g. an accidental user rule
            addLine("||doubleclick.net^")
            protected.forEach { allow(it) }
        }
        val e = FilterEngine(rules)
        assertEquals(Verdict.ALLOWED, e.check("partner.privatbank.ua"))
        assertEquals(Verdict.ALLOWED, e.check("www.privat24.ua"))
        assertEquals(Verdict.ALLOWED, e.check("diia.gov.ua"))
        assertEquals(Verdict.ALLOWED, e.check("connectivitycheck.gstatic.com"))
        assertEquals(Verdict.BLOCKED, e.check("ad.doubleclick.net"))
    }

    @Test fun aggressiveZoneListIsOffByDefault() {
        val tlds = FilterRepository.BUILTIN.first { it.id == "hagezi_tlds" }
        assertFalse(tlds.enabled)
        assertTrue(FilterRepository.BUILTIN.first { it.id == "hagezi_popup" }.enabled)
    }
}
