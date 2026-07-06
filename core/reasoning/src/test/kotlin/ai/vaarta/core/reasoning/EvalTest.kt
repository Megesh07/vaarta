package ai.vaarta.core.reasoning

import ai.vaarta.core.common.RiskEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Text-mode eval (TESTING_STRATEGY.md §6) — the product's judgment exam, run against reference
 * transcripts (no audio needed). Binding gates: scam recall by minute 3, and false-SCAM-PATTERN = 0
 * on benign calls. This is a tiny seed corpus; M2 grows it to SCAM-40 / BENIGN-60.
 */
class EvalTest {

    private val langs = listOf("en", "hi_latn", "hi")
    private fun engine() = RiskEngine(PackLoader.fromResource("/packs/core-scam-v1.json"), langs)
    private fun line(text: String, atMs: Long) =
        RiskEvent.Transcript(text, atMs, atMs + 3_000, isFinal = true, confidence = 0.9f)

    private val scamScript = listOf(
        line("Hello, this is about a parcel in your name that customs has seized with illegal items", 5_000),
        line("I am transferring you to the CBI crime branch cyber cell now", 20_000),
        line("This is officer Sharma badge number 4471, an arrest warrant and FIR are registered against you for money laundering", 45_000),
        line("You are now under digital arrest, do not disconnect and do not tell anyone in your family", 75_000),
        line("Install WhatsApp and join the video call, keep your camera on at all times", 100_000),
        line("A non bailable arrest warrant has been issued and your account will be frozen within two hours", 125_000),
        line("To verify your funds you must transfer the money to this RBI supervised account immediately", 155_000),
    )

    // A GENUINE police callback: mentions authority + FIR, but never isolates the victim or asks
    // for a transfer. The stage grammar must keep this out of SCAM_PATTERN.
    private val benignScript = listOf(
        line("Hello, this is the cyber crime police helpline returning your earlier complaint", 5_000),
        line("Your FIR has been registered and assigned to an investigating officer", 30_000),
        line("Please visit your nearest police station with a copy of your documents", 60_000),
        line("You can also track the status on the cybercrime portal, thank you", 90_000),
    )

    @Test
    fun `scam script reaches SCAM_PATTERN by minute 3`() {
        val e = engine()
        var last: RiskState? = null
        for (ev in scamScript) if (ev.atMs <= 180_000) last = e.ingest(ev)
        assertNotNull(last)
        assertEquals(
            RiskLevel.SCAM_PATTERN, last!!.level,
            "final ${last.score}/${last.level}, top=${last.topSignals.map { it.signalId }}",
        )
    }

    @Test
    fun `genuine police call never reaches SCAM_PATTERN (zero-tolerance gate)`() {
        val e = engine()
        var maxLevel = RiskLevel.OBSERVING
        var finalScore = 0
        for (ev in benignScript) {
            val s = e.ingest(ev)
            if (s.level.ordinal > maxLevel.ordinal) maxLevel = s.level
            finalScore = s.score
        }
        assertTrue(
            maxLevel.ordinal < RiskLevel.SCAM_PATTERN.ordinal,
            "benign call peaked at $maxLevel (final score $finalScore) — false SCAM_PATTERN is zero-tolerance",
        )
    }
}
