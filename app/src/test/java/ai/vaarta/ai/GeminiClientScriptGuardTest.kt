package ai.vaarta.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins [GeminiClient.hasDevanagari] — the cheap, unambiguous detector `chat()`'s language-mismatch
 * retry guard (2026-07-22) relies on to decide whether a reply drifted into Hindi script when the
 * user's own message didn't use it.
 */
class GeminiClientScriptGuardTest {

    @Test
    fun `detects Devanagari text`() {
        assertTrue(GeminiClient.hasDevanagari("यह एक स्कैम है"))
    }

    @Test
    fun `plain English has no Devanagari`() {
        assertFalse(GeminiClient.hasDevanagari("This looks like a scam"))
    }

    @Test
    fun `Hinglish (Latin-script Hindi) has no Devanagari`() {
        assertFalse(GeminiClient.hasDevanagari("Yeh ek scam lag raha hai"))
    }

    @Test
    fun `a single Devanagari character is enough to detect`() {
        assertTrue(GeminiClient.hasDevanagari("OTP का"))
    }

    @Test
    fun `blank text has no Devanagari`() {
        assertFalse(GeminiClient.hasDevanagari(""))
    }
}
