package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class ReportWriterTest {
    private val r = Report("gemini-classify", 10, 9, 1, 1, 0.2, 1, 0.25, 0.8, 0.75, 300, 900)

    @Test
    fun `markdown has a header row and the false-safe rate`() {
        val md = ReportWriter.toMarkdown(listOf(r))
        assertTrue(md.contains("False-safe"))
        assertTrue(md.contains("gemini-classify"))
        assertTrue(md.contains("0.20") || md.contains("20"))
    }

    @Test
    fun `csv first line is the header and includes falseSafeRate`() {
        val csv = ReportWriter.toCsv(listOf(r))
        assertTrue(csv.lineSequence().first().contains("falseSafeRate"))
    }
}
