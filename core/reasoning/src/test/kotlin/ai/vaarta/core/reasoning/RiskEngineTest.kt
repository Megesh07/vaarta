package ai.vaarta.core.reasoning

import ai.vaarta.core.common.IntelPack
import ai.vaarta.core.common.MatchMode
import ai.vaarta.core.common.Normalization
import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Signal
import ai.vaarta.core.common.SignalCategory
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

    // --- ADR-0003: prove the userSafe guard actually excludes a signal from scoring. It is a
    // static, pack-level "never score this, regardless of speaker" flag (AI_REASONING_ENGINE.md
    // §4.3) — appropriate ONLY for phrases that are inherently user-only, like the docs' own "1930"
    // example. This test constructs a throwaway pack rather than flipping the flag on a real
    // core-scam-v1.json signal: SIG_AUTHORITY_POLICE_ED_CUSTOMS and SIG_EXTRACTION_TRANSFER are two
    // of the strongest actual scam signals, and marking them userSafe would blind the engine to a
    // real scammer saying the exact same words. The self-echo problem those two signals cause when
    // the USER says them is instead handled dynamically by OwnWordsGate (see CoachingSupportTest).

    private fun userSafePack() = IntelPack(
        packId = "test-usersafe",
        version = "1",
        signals = listOf(
            Signal(
                id = "SIG_TEST_USER_SAFE",
                category = SignalCategory.LEGAL_THREAT,
                stage = Stage.AUTHORITY,
                weight = 50,
                patterns = mapOf("en" to listOf("1930")),
                match = MatchMode.EXACT,
                userSafe = true,
            ),
        ),
    )

    @Test
    fun `userSafe signal never fires regardless of who says it`() {
        val e = RiskEngine(userSafePack(), langs)
        val s = e.ingest(t("I will call the 1930 helpline", 5_000))
        assertEquals(0, s.score, "userSafe signal must be excluded from scoring")
        assertTrue(s.topSignals.isEmpty())
    }

    @Test
    fun `investment lure with urgency and extraction reaches scam pattern`() {
        val e = engine()
        e.ingest(t("Double your money in 7 days with our guaranteed trading plan", 5_000))
        e.ingest(t("This offer closes within two hours, act immediately", 20_000))
        val s = e.ingest(t("Transfer the money to this UPI id to start your investment", 40_000))
        assertTrue(s.score > 0, "investment hook + urgency + extraction should score above zero")
    }

    @Test
    fun `job task lure is detected as a hook signal`() {
        val e = engine()
        val s = e.ingest(t("Earn money doing simple tasks from home, work from home job available", 5_000))
        assertTrue(s.stage == Stage.HOOK, "job-task hook phrase should register at HOOK stage")
    }

    @Test
    fun `loan app hook is detected`() {
        val e = engine()
        val s = e.ingest(t("Your instant loan of fifty thousand rupees has been approved, no documents required", 5_000))
        assertTrue(s.stage == Stage.HOOK)
    }

    @Test
    fun `lottery hook is detected`() {
        val e = engine()
        val s = e.ingest(t("Congratulations, you have won the KBC lottery prize of 25 lakh rupees", 5_000))
        assertTrue(s.stage == Stage.HOOK)
    }

    @Test
    fun `electricity disconnection hook is detected`() {
        val e = engine()
        val s = e.ingest(t("Your electricity connection will be disconnected today for non payment of bill", 5_000))
        assertTrue(s.stage == Stage.HOOK)
    }

    @Test
    fun `upi wrong payment refund hook is detected`() {
        val e = engine()
        val s = e.ingest(t("Sorry sir I sent money to your account by mistake, please refund the wrong payment", 5_000))
        assertTrue(s.stage == Stage.HOOK)
    }

    @Test
    fun `courier cod otp hook is detected`() {
        val e = engine()
        val s = e.ingest(t("Your cash on delivery parcel needs an OTP confirmation before dispatch", 5_000))
        assertTrue(s.stage == Stage.HOOK)
    }
}
