package app.cleanweb

import app.cleanweb.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscTest {
    @Test fun comparesVersions() {
        assertTrue(UpdateChecker.compare("1.2.1", "1.2.0") > 0)
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.9") > 0)
        assertEquals(0, UpdateChecker.compare("1.2", "1.2.0"))
        assertTrue(UpdateChecker.compare("1.1.9", "1.2.0") < 0)
    }
}
