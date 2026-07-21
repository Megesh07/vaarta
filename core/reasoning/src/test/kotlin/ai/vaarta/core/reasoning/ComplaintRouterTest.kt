package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplaintRouterTest {

    private val pb = ComplaintPlaybookLoader.bundled()

    @Test
    fun `digital arrest with money lost routes NCRP first`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-01", moneyLost = true)
        assertEquals("ncrp", r.first().id)
    }

    @Test
    fun `spam-number scam routes to Chakshu, never NCRP`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-08", moneyLost = false)
        assertEquals(listOf("chakshu"), r.map { it.id })
    }

    @Test
    fun `feeder code SC-02 reaches both, NCRP first when money lost`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-02", moneyLost = true)
        assertTrue(r.map { it.id }.containsAll(listOf("ncrp", "chakshu")))
        assertEquals("ncrp", r.first().id)
    }

    @Test
    fun `unknown or null scam code returns all destinations for the user to pick`() {
        val r = ComplaintRouter.route(pb, scamCode = null, moneyLost = false)
        assertEquals(pb.destinations.size, r.size)
    }
}
