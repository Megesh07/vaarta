package ai.vaarta.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Redesign spec §5 (Part C): the coach's domain knowledge generalizes from one fixed digital-arrest
 * script to universal manipulation-pattern reasoning, but the safety-critical HARD RULES text must
 * survive byte-for-byte (spec §2 invariant 6) — this is the regression guard for that.
 */
class CoachPromptGeneralizationTest {

    // The exact HARD RULES sentence from the pre-generalization CoachPrompt — copied verbatim as the
    // regression anchor. If this substring disappears, the safety rule was accidentally reworded.
    private val hardRulesAnchor =
        "you must NEVER, in the warning or any reply: tell the user to pay, transfer"

    private val hardRulesAnchorShared =
        "you must NEVER: tell the user to pay/transfer/comply"

    @Test
    fun `CoachPrompt HARD RULES survive the generalization byte-for-byte`() {
        assertTrue(CoachPrompt.INSTRUCTION.contains(hardRulesAnchor), "CoachPrompt HARD RULES anchor missing")
    }

    @Test
    fun `SharedScamPrompt HARD RULES survive the generalization byte-for-byte`() {
        assertTrue(SharedScamPrompt.INSTRUCTION.contains(hardRulesAnchorShared), "SharedScamPrompt HARD RULES anchor missing")
    }

    @Test
    fun `CoachPrompt reasons from manipulation patterns, not one fixed script`() {
        for (term in listOf("authority-impersonation", "urgency-manufacturing", "isolation-demanding", "financial-extraction")) {
            assertTrue(CoachPrompt.INSTRUCTION.contains(term), "CoachPrompt must name the $term pattern")
        }
    }

    @Test
    fun `CoachPrompt explicitly says known families are illustrative, not exhaustive`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("not an exhaustive list") ||
                CoachPrompt.INSTRUCTION.contains("not exhaustive"),
            "CoachPrompt must disclaim the known-family list is illustrative only",
        )
    }

    @Test
    fun `CoachPrompt still covers the unmatched-scam fallback (never silence)`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("no known family") || CoachPrompt.INSTRUCTION.contains("matches no known"),
            "CoachPrompt must instruct the model to still coach when nothing matches a known family",
        )
    }

    @Test
    fun `CoachPrompt instructs adversarial probing questions, not just the one worked example`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("calibrated") && CoachPrompt.INSTRUCTION.contains("cannot"),
            "CoachPrompt must instruct the model to generate claim-calibrated verify questions a scripted caller cannot answer",
        )
    }

    @Test
    fun `CoachPrompt no longer claims digital-arrest is the only task`() {
        assertFalse(
            CoachPrompt.INSTRUCTION.contains("SPECIALIZED real-time copilot that coaches a potential victim through a\n        suspected 'digital arrest' scam call"),
            "CoachPrompt must be generalized beyond digital-arrest-only framing",
        )
    }
}
