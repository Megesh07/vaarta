package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

class GeminiClassifyParseTest {
    // Mimics the real generateContent shape: JSON-in-text inside candidates[0].content.parts[0].text.
    private val cannedScam = """
      {"candidates":[{"content":{"parts":[{"text":"Recent advisories describe CBI 'digital arrest' calls demanding transfers.\n{\"scamType\":\"digital-arrest\",\"concern\":\"SCAM_PATTERN\",\"benign\":false}"}]}}]}
    """.trimIndent()

    @Test
    fun `parses scamType, concern, benign from grounded JSON-in-text`() {
        val v = parseClassifyVerdict("gemini-classify", cannedScam, 250)
        assertEquals("digital-arrest", v.variant)
        assertEquals(Concern.SCAM_PATTERN, v.concern)
        assertEquals(true, v.isScam)
        assertEquals(250, v.latencyMs)
        assertEquals(null, v.error)
    }

    @Test
    fun `malformed body yields an error verdict (fail closed), not a benign one`() {
        val v = parseClassifyVerdict("gemini-classify", "not json", 10)
        assertFalse(v.error == null)
        assertEquals(null, v.isScam)
    }
}
