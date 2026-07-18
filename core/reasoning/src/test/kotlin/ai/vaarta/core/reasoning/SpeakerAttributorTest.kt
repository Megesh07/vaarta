package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeakerAttributorTest {

    @Test
    fun `short segment is always unverified regardless of a positive voiceprint match`() {
        assertEquals(SpeakerLabel.UNVERIFIED, SpeakerAttributor.attribute(durationMs = 1_000, verifiedByVoiceprint = true))
    }

    @Test
    fun `segment at or above 1500ms with a voiceprint match is labeled USER`() {
        assertEquals(SpeakerLabel.USER, SpeakerAttributor.attribute(durationMs = 1_500, verifiedByVoiceprint = true))
    }

    @Test
    fun `segment at or above 1500ms without a voiceprint match is unverified`() {
        assertEquals(SpeakerLabel.UNVERIFIED, SpeakerAttributor.attribute(durationMs = 3_000, verifiedByVoiceprint = false))
    }

    @Test
    fun `fail-safe property- USER can never be produced without both a long-enough segment AND a match`() {
        val cases = listOf(
            500L to false, 500L to true, 1_499L to true,
            1_500L to false, 10_000L to false,
        )
        for ((duration, verified) in cases) {
            assertEquals(
                SpeakerLabel.UNVERIFIED,
                SpeakerAttributor.attribute(duration, verified),
                "duration=$duration verified=$verified must not produce USER",
            )
        }
    }
}
