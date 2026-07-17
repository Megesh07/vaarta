package ai.vaarta.ai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Spec §3A.1: every user-facing prompt carries the one shared India context block, so
 * India-first can never silently drift out of an AI surface.
 */
class IndiaContextTest {

    private val userFacingPrompts = mapOf(
        "ChatPrompt" to ChatPrompt.INSTRUCTION,
        "AwarenessPrompt.FEED" to AwarenessPrompt.FEED,
        "AwarenessPrompt.SUMMARY_SYSTEM" to AwarenessPrompt.SUMMARY_SYSTEM,
        "CoachPrompt" to CoachPrompt.INSTRUCTION,
        "AudioAnalyzePrompt" to AudioAnalyzePrompt.INSTRUCTION,
        "SharedScamPrompt" to SharedScamPrompt.INSTRUCTION,
    )

    @Test
    fun `every user-facing prompt contains the India anchor block`() {
        for ((name, prompt) in userFacingPrompts) {
            assertTrue(prompt.contains(IndiaContext.BLOCK), "$name is missing IndiaContext.BLOCK")
        }
    }

    @Test
    fun `the anchor block pins the Indian help rail and forbids foreign resources`() {
        for (required in listOf("1930", "cybercrime.gov.in", "Sanchar Saathi", "₹", "911", "UPI")) {
            assertTrue(IndiaContext.BLOCK.contains(required), "anchor block must mention $required")
        }
    }
}
