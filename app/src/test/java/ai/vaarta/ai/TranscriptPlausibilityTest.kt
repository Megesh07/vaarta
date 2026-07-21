package ai.vaarta.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression cases for `task_b91d4a05`: on a real-device live-call test (2026-07-19), Gemini
 * Live's `inputTranscription` hallucinated fluent Tamil/Telugu text — reproduced verbatim below —
 * instead of transcribing the actual English audio, and kept fabricating new text from
 * near-silence. VAARTA only supports EN/HI/Hinglish, so this text must never reach the risk
 * engine or the coach.
 */
class TranscriptPlausibilityTest {

    @Test
    fun `accepts plain English`() {
        assertTrue(TranscriptPlausibility.isPlausible("Sir, I am calling from the cyber crime branch"))
    }

    @Test
    fun `accepts Hindi Devanagari`() {
        assertTrue(TranscriptPlausibility.isPlausible("यह एक स्कैम कॉल है"))
    }

    @Test
    fun `accepts Hinglish (Latin-script Hindi)`() {
        assertTrue(TranscriptPlausibility.isPlausible("aapka aadhaar card block ho jayega"))
    }

    @Test
    fun `accepts mixed Hindi and English`() {
        assertTrue(TranscriptPlausibility.isPlausible("आपका आधार कार्ड scam में इस्तेमाल हुआ है"))
    }

    @Test
    fun `rejects the actual hallucinated Tamil transcript observed live`() {
        assertFalse(
            TranscriptPlausibility.isPlausible(
                "சரி நம்ம டேட்டா பேர் வரும் பாத்தீங்கன்னா ஆதச்சு",
            ),
        )
    }

    @Test
    fun `rejects the actual hallucinated Telugu transcript observed live`() {
        assertFalse(
            TranscriptPlausibility.isPlausible(
                "ఇక్కడ చూడకంసాని ఎల్లా ఉంటాయి",
            ),
        )
    }

    @Test
    fun `rejects blank text`() {
        assertFalse(TranscriptPlausibility.isPlausible(""))
        assertFalse(TranscriptPlausibility.isPlausible("   "))
    }

    @Test
    fun `rejects digits-and-punctuation-only text`() {
        assertFalse(TranscriptPlausibility.isPlausible("123456, ..."))
    }
}
