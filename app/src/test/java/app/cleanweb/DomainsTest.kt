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
