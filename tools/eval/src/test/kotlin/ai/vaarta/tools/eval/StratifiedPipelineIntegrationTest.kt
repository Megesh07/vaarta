package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Proves the FULL pipeline works end-to-end: real JSON -> CaseLoader.load -> Scorer.scoreStratified
 * -> ReportWriter.toMarkdownStratified / toCsvStratified.
 *
 * Every other test in this module exercises exactly one seam in isolation (CaseLoaderTest never
 * scores or renders; ScorerStratifiedTest hand-builds TranscriptCase/Verdict pairs and never
 * touches JSON; ReportWriterStratifiedTest renders a hand-built, synthetic StratifiedReport that
 * no real scorer produces). None of them prove the seams fit together. This test is the one place
 * that runs a real case file through every real component in sequence.
 */
class StratifiedPipelineIntegrationTest {

    // Realistic, hand-written cases (not the placeholder single-BLATANT-pair fixture used
    // elsewhere) spanning all four distinct strata via five cases: a caught BLATANT_SCAM, a MISSED
    // BLATANT_SCAM (the NOT READY trigger), a BLATANT_BENIGN ordinary call, a caught HARD_SUBTLE_SCAM,
    // and a HARD_SUBTLE_BENIGN false-alarm trap that must NOT be flagged.
    private val casesJson = """
        [
          {
            "id": "int-blatant-scam-caught",
            "transcript": "Caller: This is Officer Verma from the CBI cybercrime cell. Your Aadhaar has been linked to a money-laundering case. You are under digital arrest — stay on this video call, do not contact anyone, and transfer 2,00,000 rupees to the RBI verification account immediately or a warrant will be issued.",
            "language": "en",
            "source": "synthetic-integration-blatant-scam",
            "provenance": "SYNTHETIC",
            "groundTruth": { "isScam": true, "variant": "digital-arrest", "concern": "SCAM_PATTERN", "difficulty": "BLATANT" }
          },
          {
            "id": "int-blatant-scam-missed",
            "transcript": "Caller: This is Inspector Rao, Mumbai Police cyber cell. A parcel booked on your Aadhaar was found carrying narcotics. Stay on this call, share your Aadhaar number and the OTP just sent to you, or you will be placed under digital arrest today.",
            "language": "en",
            "source": "synthetic-integration-blatant-scam",
            "provenance": "SYNTHETIC",
            "groundTruth": { "isScam": true, "variant": "digital-arrest", "concern": "SCAM_PATTERN", "difficulty": "BLATANT" }
          },
          {
            "id": "int-blatant-benign-ok",
            "transcript": "Caller: Hi, this is Priya from the electricity board. Just confirming — did you already pay this month's bill online, or should I note it as pending? No action needed if you've already paid.",
            "language": "en",
            "source": "synthetic-integration-benign-ordinary",
            "provenance": "SYNTHETIC",
            "groundTruth": { "isScam": false, "variant": "", "concern": "OBSERVING", "difficulty": "BLATANT" }
          },
          {
            "id": "int-subtle-scam-caught",
            "transcript": "Caller: Hi, this is your bank's loyalty desk calling. We noticed an unusual login, nothing to be alarmed about, but to keep the account safe could you just read out the OTP you received a moment ago so we can lock the session from our end?",
            "language": "en",
            "source": "synthetic-integration-subtle-scam",
            "provenance": "SYNTHETIC",
            "groundTruth": { "isScam": true, "variant": "otp-phishing", "concern": "HIGH_RISK", "difficulty": "HARD_SUBTLE" }
          },
          {
            "id": "int-subtle-benign-trap",
            "transcript": "Caller: Good afternoon, this is customs clearance calling about your international parcel. It has been flagged for a routine documentation check, nothing to worry about — could you confirm the invoice number on file so we can release it for delivery today?",
            "language": "en",
            "source": "synthetic-integration-benign-trap",
            "provenance": "SYNTHETIC",
            "groundTruth": { "isScam": false, "variant": "", "concern": "CAUTION", "difficulty": "HARD_SUBTLE" }
          }
        ]
    """.trimIndent()

    private val cases = CaseLoader.load(casesJson)

    private fun verdictFor(caseId: String): Verdict = when (caseId) {
        // Caught: judge correctly flags the blatant digital-arrest script.
        "int-blatant-scam-caught" ->
            Verdict("integration-judge", true, Concern.SCAM_PATTERN, "digital-arrest", 0.95, 120, "{}", null)
        // MISSED: judge wrongly calls a blatant digital-arrest script safe. This is the case
        // that must surface in blatantScamMisses and trigger the NOT READY callout.
        "int-blatant-scam-missed" ->
            Verdict("integration-judge", false, Concern.OBSERVING, "", 0.4, 90, "{}", null)
        // Correctly passed: judge correctly flags an ordinary benign utility-company call as not a scam.
        "int-blatant-benign-ok" ->
            Verdict("integration-judge", false, Concern.OBSERVING, "", 0.1, 60, "{}", null)
        // Caught: judge correctly flags the subtle OTP-phishing call despite no trigger words.
        "int-subtle-scam-caught" ->
            Verdict("integration-judge", true, Concern.HIGH_RISK, "otp-phishing", 0.8, 150, "{}", null)
        // Correctly passed: judge is not fooled by the scary-sounding but benign customs call.
        "int-subtle-benign-trap" ->
            Verdict("integration-judge", false, Concern.CAUTION, "", 0.2, 80, "{}", null)
        else -> error("no verdict scripted for case $caseId")
    }

    private val pairs: List<Pair<TranscriptCase, Verdict>> = cases.map { it to verdictFor(it.id) }

    private val report: StratifiedReport = Scorer.scoreStratified("integration-judge", pairs)

    @Test
    fun `real JSON scored by the real scorer renders every present stratum and the NOT READY callout in markdown`() {
        assertEquals(5, cases.size)

        val md = ReportWriter.toMarkdownStratified(listOf(report))

        // Every stratum actually present in the real, scored data must show up in the render —
        // not a hand-built StratifiedReport that only ever contained what the test author typed.
        val strataPresent = pairs.map { (c, _) -> c.groundTruth.stratum() }.distinct()
        assertEquals(setOf(Stratum.BLATANT_SCAM, Stratum.BLATANT_BENIGN, Stratum.HARD_SUBTLE_SCAM, Stratum.HARD_SUBTLE_BENIGN), strataPresent.toSet())
        for (stratum in strataPresent) {
            assertTrue(md.contains(stratum.name), "markdown missing stratum $stratum")
        }

        // The blatant miss produced by the REAL scorer from REAL verdicts must trip the guardrail.
        assertTrue(md.contains("NOT READY"))
        assertTrue(md.contains("int-blatant-scam-missed"))
    }

    @Test
    fun `real JSON scored by the real scorer produces a CSV row for every stratum actually present`() {
        val csv = ReportWriter.toCsvStratified(listOf(report))
        val dataLines = csv.lineSequence().drop(1).filter { it.isNotBlank() }.toList()

        val strataPresent = pairs.map { (c, _) -> c.groundTruth.stratum() }.distinct()
        assertEquals(strataPresent.size, dataLines.size)
        for (stratum in strataPresent) {
            assertTrue(dataLines.any { it.contains(stratum.name) }, "csv missing row for $stratum")
        }
    }
}
