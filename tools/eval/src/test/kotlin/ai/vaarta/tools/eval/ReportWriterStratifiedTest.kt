package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class ReportWriterStratifiedTest {
    private fun report(stratum: Stratum, r: Report) = stratum to r
    private val flat = Report("j", 1, 1, 0, 0, 0.0, 0, 0.0, 1.0, 1.0, 100, 100)

    private val cleanReport = StratifiedReport(
        judgeId = "gemini-classify",
        overall = flat,
        byStratum = mapOf(
            Stratum.BLATANT_SCAM to flat,
            Stratum.HARD_SUBTLE_SCAM to flat,
            Stratum.BLATANT_BENIGN to flat,
            Stratum.HARD_SUBTLE_BENIGN to flat,
        ),
        blatantScamMisses = emptyList(),
    )

    private val notReadyReport = cleanReport.copy(blatantScamMisses = listOf("da-013", "da-027"))

    @Test
    fun `markdown includes overall row and a per-stratum breakdown`() {
        val md = ReportWriter.toMarkdownStratified(listOf(cleanReport))
        assertTrue(md.contains("gemini-classify"))
        assertTrue(md.contains("BLATANT_SCAM"))
        assertTrue(md.contains("HARD_SUBTLE_SCAM"))
        assertTrue(md.contains("BLATANT_BENIGN"))
        assertTrue(md.contains("HARD_SUBTLE_BENIGN"))
    }

    @Test
    fun `markdown has no NOT READY callout when no blatant scam was missed`() {
        val md = ReportWriter.toMarkdownStratified(listOf(cleanReport))
        assertFalse(md.contains("NOT READY"))
    }

    @Test
    fun `markdown has a loud NOT READY callout naming every missed blatant scam case`() {
        val md = ReportWriter.toMarkdownStratified(listOf(notReadyReport))
        assertTrue(md.contains("NOT READY"))
        assertTrue(md.contains("da-013"))
        assertTrue(md.contains("da-027"))
    }

    @Test
    fun `csv includes a stratum column`() {
        val csv = ReportWriter.toCsvStratified(listOf(cleanReport))
        assertTrue(csv.lineSequence().first().contains("stratum"))
    }
}
