package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ScorerStratifiedTest {
    private fun scamCase(id: String, difficulty: Difficulty) =
        TranscriptCase(id, "t", "en", GroundTruth(true, "digital-arrest", Concern.SCAM_PATTERN, difficulty), "s", Provenance.SYNTHETIC)
    private fun benignCase(id: String, difficulty: Difficulty) =
        TranscriptCase(id, "t", "en", GroundTruth(false, "", Concern.OBSERVING, difficulty), "s", Provenance.SYNTHETIC)
    private fun verdict(isScam: Boolean?, concern: Concern?, error: String? = null) =
        Verdict("j", isScam, concern, "digital-arrest", 0.9, 100, "{}", error)

    @Test
    fun `scoreStratified breaks metrics down by stratum and never blends them`() {
        val pairs = listOf(
            // BLATANT_SCAM: one caught, one MISSED (this must trigger blatantScamMisses)
            scamCase("blatant-scam-caught", Difficulty.BLATANT) to verdict(true, Concern.SCAM_PATTERN),
            scamCase("blatant-scam-missed", Difficulty.BLATANT) to verdict(false, Concern.OBSERVING),
            // HARD_SUBTLE_SCAM: one caught (a miss here must NOT appear in blatantScamMisses)
            scamCase("subtle-scam-caught", Difficulty.HARD_SUBTLE) to verdict(true, Concern.SCAM_PATTERN),
            // BLATANT_BENIGN: correctly passed
            benignCase("blatant-benign-ok", Difficulty.BLATANT) to verdict(false, Concern.OBSERVING),
            // HARD_SUBTLE_BENIGN: a false alarm (the "sounds scary but isn't" trap failing)
            benignCase("subtle-benign-falsealarm", Difficulty.HARD_SUBTLE) to verdict(true, Concern.HIGH_RISK),
        )

        val report = Scorer.scoreStratified("j", pairs)

        // Overall still matches the plain score() function (no new logic duplicated).
        assertEquals(Scorer.score("j", pairs), report.overall)

        // Per-stratum: BLATANT_SCAM has 1 caught + 1 missed -> recall 0.5, NOT blended with the
        // subtle stratum's clean catch.
        val blatantScam = report.byStratum.getValue(Stratum.BLATANT_SCAM)
        assertEquals(2, blatantScam.total)
        assertEquals(0.5, blatantScam.recall, 1e-9)

        val subtleScam = report.byStratum.getValue(Stratum.HARD_SUBTLE_SCAM)
        assertEquals(1, subtleScam.total)
        assertEquals(1.0, subtleScam.recall, 1e-9)

        val blatantBenign = report.byStratum.getValue(Stratum.BLATANT_BENIGN)
        assertEquals(0.0, blatantBenign.falseAlarmRate, 1e-9)

        val subtleBenign = report.byStratum.getValue(Stratum.HARD_SUBTLE_BENIGN)
        assertEquals(1.0, subtleBenign.falseAlarmRate, 1e-9)

        // Zero partial credit: exactly the one BLATANT_SCAM miss is named, by ID.
        assertEquals(listOf("blatant-scam-missed"), report.blatantScamMisses)
    }

    @Test
    fun `blatantScamMisses is empty when no blatant scam was missed`() {
        val pairs = listOf(
            scamCase("blatant-scam-caught", Difficulty.BLATANT) to verdict(true, Concern.SCAM_PATTERN),
            scamCase("subtle-scam-missed", Difficulty.HARD_SUBTLE) to verdict(false, Concern.OBSERVING),
        )
        assertTrue(Scorer.blatantScamMisses(pairs).isEmpty())
    }
}
