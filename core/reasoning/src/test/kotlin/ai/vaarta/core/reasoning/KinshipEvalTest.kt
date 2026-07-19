package ai.vaarta.core.reasoning

import ai.vaarta.core.common.RiskEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Eval for the SC-09 family-emergency impersonation family (pack v3). A scam that claims kinship,
 * a new number, secrecy and urgent money must reach SCAM_PATTERN; a GENUINE family emergency that
 * never demands secret urgent money to an unfamiliar account must NOT (zero-tolerance, mirrors
 * EvalTest's benign-police gate).
 */
class KinshipEvalTest {

    private val langs = listOf("en", "hi_latn", "hi")
    private fun engine() = RiskEngine(PackLoader.fromResource("/packs/core-scam-v1.json"), langs)
    private fun line(text: String, atMs: Long) =
        RiskEvent.Transcript(text, atMs, atMs + 3_000, isFinal = true, confidence = 0.9f)

    private val kinshipScam = listOf(
        line("Hello beta it's me, I am in serious trouble please listen carefully", 5_000),
        line("This is my new number, my old phone is broken, do not call the old one", 25_000),
        line("I had an accident and the police are involved, please do not tell papa or anyone", 55_000),
        line("You must transfer the money to this account right now to settle it", 90_000),
    )

    // A REAL family emergency: kinship + distress, but no secrecy, no new-number misdirection,
    // no demand to wire money to an unfamiliar account. Must stay out of SCAM_PATTERN.
    private val genuineEmergency = listOf(
        line("Hi it's me, I'm okay but I had a small accident on the way home", 5_000),
        line("Can you come pick me up near the hospital, I am with the doctor now", 40_000),
        line("Tell mom I'll be a bit late, nothing serious, see you soon", 80_000),
    )

    @Test
    fun `kinship impersonation scam reaches SCAM_PATTERN`() {
        val e = engine()
        var last: RiskState? = null
        for (ev in kinshipScam) last = e.ingest(ev)
        assertEquals(
            RiskLevel.SCAM_PATTERN, last!!.level,
            "final ${last.score}/${last.level}, top=${last.topSignals.map { it.signalId }}",
        )
    }

    @Test
    fun `genuine family emergency never reaches SCAM_PATTERN`() {
        val e = engine()
        var maxLevel = RiskLevel.OBSERVING
        for (ev in genuineEmergency) {
            val s = e.ingest(ev)
            if (s.level.ordinal > maxLevel.ordinal) maxLevel = s.level
        }
        assertTrue(
            maxLevel.ordinal < RiskLevel.SCAM_PATTERN.ordinal,
            "genuine emergency peaked at $maxLevel — false SCAM_PATTERN is zero-tolerance",
        )
    }
}
