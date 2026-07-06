package ai.vaarta.core.reasoning

import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiskEngineTest {

    private val langs = listOf("en", "hi_latn", "hi")
    private fun engine() = RiskEngine(PackLoader.fromResource("/packs/core-scam-v1.json"), langs)
    private fun t(text: String, atMs: Long) =
        RiskEvent.Transcript(text, atMs, atMs + 2_000, isFinal = true, confidence = 1.0f)

    @Test
    fun `lone authority claim does not reach scam pattern`() {
        val s = engine().ingest(t("Sir I am calling from the CBI crime branch", 5_000))
        assertTrue(
            s.level.ordinal < RiskLevel.SCAM_PATTERN.ordinal,
            "authority-only must not be SCAM_PATTERN — was ${s.level} @ ${s.score}",
        )
    }

    @Test
    fun `full digital arrest progression reaches scam pattern`() {
        val e = engine()
        e.ingest(t("There is a parcel in your name seized by customs", 5_000))
        e.ingest(t("I am from the CBI, there is an arrest warrant and FIR against you", 30_000))
        e.ingest(t("You are under digital arrest, do not tell anyone in your family", 70_000))
        e.ingest(t("Join the WhatsApp video call and keep your camera on", 95_000))
        val s = e.ingest(t("Transfer the money to this RBI supervised account to verify your funds", 150_000))
        assertEquals(RiskLevel.SCAM_PATTERN, s.level)
        assertEquals(Stage.EXTRACTION, s.stage)
    }

    @Test
    fun `manual mode cues alone can drive the score (audio-free parity)`() {
        val e = engine()
        e.ingest(RiskEvent.ManualCue("CUE_CLAIMS_AUTHORITY", 5_000))
        e.ingest(RiskEvent.ManualCue("CUE_THREATENS_ARREST", 20_000))
        e.ingest(RiskEvent.ManualCue("CUE_DONT_TELL_ANYONE", 40_000))
        val s = e.ingest(RiskEvent.ManualCue("CUE_DEMANDS_MONEY", 60_000))
        assertEquals(RiskLevel.SCAM_PATTERN, s.level)
    }

    @Test
    fun `known caller disables scoring`() {
        val e = engine()
        e.ingest(RiskEvent.UserAction(RiskEngine.ACTION_KNOWN_CALLER, 1_000))
        val s = e.ingest(t("CBI arrest warrant transfer the money to RBI account", 10_000))
        assertEquals(RiskLevel.OBSERVING, s.level)
        assertEquals(0, s.score)
    }

    @Test
    fun `extraction floor is sticky for the rest of the session`() {
        val e = engine()
        e.ingest(t("Transfer the money to this RBI supervised account", 10_000))
        val later = e.ingest(t("okay thank you sir", 200_000))
        assertTrue(later.score >= 75, "extraction floor must hold — was ${later.score}")
    }

    @Test
    fun `fuzzy matching survives ASR word-boundary errors`() {
        // "digital a rest" is a realistic ASR mistranscription of "digital arrest".
        val e = engine()
        val s = e.ingest(t("you are under digital a rest do not tell anyone", 5_000))
        assertTrue(s.topSignals.any { it.signalId == "SIG_ISOLATION_SECRECY" || it.signalId == "SIG_LEGAL_THREAT" })
    }
}
