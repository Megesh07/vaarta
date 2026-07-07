package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Enforces IMPLEMENTATION_GUARDRAILS.md ALWAYS #10: every signal the engine can learn from a
 * transcript must have an equivalent Manual Mode cue, so detection is never audio-exclusive.
 * This is a data-invariant test on the bundled pack itself, not engine behavior — it exists so a
 * future signal added without a `manualCue` fails CI instead of silently reappearing as a gap
 * (as three signals did until this pack revision: SIG_LEGITIMACY_THEATER, SIG_IDENTITY_PHISH,
 * SIG_ESCALATION_DOCS).
 */
class PackParityTest {

    @Test
    fun `every signal in the bundled pack has a Manual Mode cue`() {
        val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
        val missing = pack.signals.filter { it.manualCue.isNullOrBlank() }
        assertTrue(
            missing.isEmpty(),
            "Signals missing Manual Mode parity: ${missing.map { it.id }} — every signal needs a manualCue (GUARDRAILS ALWAYS #10)",
        )
    }
}
