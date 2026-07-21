package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Panic-personalization wire parsing (redesign spec §A2) — [CoachingWireParser.parsePanicPersonalization]. */
class PanicPersonalizationParserTest {

    @Test
    fun `parses a well-formed personalization`() {
        val raw = """
            {"heading":"This looks like a digital-arrest scam.",
             "steps":["Ask for the officer's badge number and station.","Call the station back on its official published number, not one they give you."]}
        """.trimIndent()
        val result = CoachingWireParser.parsePanicPersonalization(raw)
        assertNotNull(result)
        assertEquals("This looks like a digital-arrest scam.", result!!.heading)
        assertEquals(2, result.steps.size)
    }

    @Test
    fun `fails closed on a blank heading`() {
        val raw = """{"heading":"","steps":["do something"]}"""
        assertNull(CoachingWireParser.parsePanicPersonalization(raw))
    }

    @Test
    fun `fails closed when every step is blank`() {
        val raw = """{"heading":"Something's off.","steps":["", "   "]}"""
        assertNull(CoachingWireParser.parsePanicPersonalization(raw))
    }

    @Test
    fun `fails closed on an empty steps array (model chose to say nothing extra)`() {
        val raw = """{"heading":"Not specific enough.","steps":[]}"""
        assertNull(CoachingWireParser.parsePanicPersonalization(raw))
    }

    @Test
    fun `caps steps at 4`() {
        val raw = """{"heading":"h","steps":["a","b","c","d","e","f"]}"""
        val result = CoachingWireParser.parsePanicPersonalization(raw)
        assertEquals(4, result!!.steps.size)
    }

    @Test
    fun `drops blank steps but keeps the real ones`() {
        val raw = """{"heading":"h","steps":["","real one",""]}"""
        val result = CoachingWireParser.parsePanicPersonalization(raw)
        assertEquals(1, result!!.steps.size)
        assertEquals("real one", result.steps[0])
    }

    @Test
    fun `fails closed on malformed or null JSON`() {
        assertNull(CoachingWireParser.parsePanicPersonalization(null))
        assertNull(CoachingWireParser.parsePanicPersonalization(""))
        assertNull(CoachingWireParser.parsePanicPersonalization("not json"))
    }
}
