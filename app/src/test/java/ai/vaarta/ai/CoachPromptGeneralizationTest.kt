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

    // --- Adaptive empathy + severity-calibration (2026-07-21): the warning must read the user's and
    // caller's emotional state and size its tone/length to how confident the scam is. These lock the
    // behavior in the prompt text so a future edit can't silently drop it back to a flat status line.

    @Test
    fun `CoachPrompt reads emotion — caller manipulation tone and user's likely state`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("EMOTION"),
            "CoachPrompt must instruct the model to read emotion",
        )
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("frightened") || CoachPrompt.INSTRUCTION.contains("scared"),
            "CoachPrompt must acknowledge the user is often frightened once threats land",
        )
    }

    @Test
    fun `CoachPrompt calibrates tone and length to the current risk level`() {
        assertTrue(CoachPrompt.INSTRUCTION.contains("RISK LEVEL"), "CoachPrompt must be given the risk level")
        assertTrue(CoachPrompt.INSTRUCTION.contains("CALIBRATE"), "CoachPrompt must instruct calibration to severity")
        // Both poles must be named so tone actually varies: light when suspicious, firm when confirmed.
        assertTrue(CoachPrompt.INSTRUCTION.contains("OBSERVING") && CoachPrompt.INSTRUCTION.contains("SCAM_PATTERN"),
            "CoachPrompt must distinguish the merely-suspicious pole from the confirmed-scam pole")
    }

    @Test
    fun `CoachPrompt leads with reassurance when a scam is confirmed and the user may be scared`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("reassur"),
            "CoachPrompt must instruct a grounding reassurance for a frightened user on a confirmed scam",
        )
    }

    @Test
    fun `CoachPrompt reply count is adaptive, not a fixed 2-to-3`() {
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("1 to 3"),
            "CoachPrompt must allow 1 to 3 replies so a mildly-suspicious call gets a lighter touch",
        )
        assertTrue(
            CoachPrompt.INSTRUCTION.contains("not a fixed count"),
            "CoachPrompt must tell the model to choose reply count from the situation, not a fixed number",
        )
    }
}
