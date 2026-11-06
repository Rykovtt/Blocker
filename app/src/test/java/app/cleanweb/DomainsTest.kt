package app.cleanweb

import app.cleanweb.filter.Domains
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainsTest {
    @Test fun siteOfHost() {
        assertEquals("kingads.digital", Domains.site("ads6.ads.kingads.digital"))
        assertEquals("example.com.ua", Domains.site("www.example.com.ua"))
        assertEquals("example.com", Domains.site("example.com"))
    }

    @Test fun hostsFromSharedText() {
        val text = "Look https://ads6.ads.kingads.digital/tracking/pc?adid=T1793 and http://Foo.Example.org/x"
        assertEquals(listOf("ads6.ads.kingads.digital", "foo.example.org"), Domains.hostsInText(text))
    }
}

class TldRuleTest {
    private fun engine(vararg l: String) =
        app.cleanweb.filter.FilterEngine(app.cleanweb.filter.RuleSet().apply { l.forEach { addLine(it) } })

    @Test fun blocksAbusedTldWithExceptions() {
        val e = engine("||*.win^\$denyallow=patriots.win|realdeals.win", "||bid^")
        assertEquals(app.cleanweb.filter.Verdict.BLOCKED, e.check("ofjvnvjf.win"))
        assertEquals(app.cleanweb.filter.Verdict.BLOCKED, e.check("a.b.hazilloorty.win"))
        assertEquals(app.cleanweb.filter.Verdict.NONE, e.check("patriots.win"))
        assertEquals(app.cleanweb.filter.Verdict.NONE, e.check("www.realdeals.win"))
        assertEquals(app.cleanweb.filter.Verdict.BLOCKED, e.check("x.bid"))
        assertEquals(app.cleanweb.filter.Verdict.NONE, e.check("example.com"))
        assertEquals(app.cleanweb.filter.Verdict.NONE, e.check("win"))
    }
}
