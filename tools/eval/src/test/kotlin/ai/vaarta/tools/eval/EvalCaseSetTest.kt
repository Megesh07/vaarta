package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Guards the real 184-case eval set shipped as the runBench default resource — a broken or
 *  drifted resource here would silently produce a meaningless benchmark run. */
class EvalCaseSetTest {
    private val cases = CaseLoader.load(
        this::class.java.getResource("/vaarta-eval-cases.json")!!.readText()
    )

    @Test
    fun `loads exactly 184 cases with unique ids`() {
        assertEquals(184, cases.size)
        assertEquals(184, cases.map { it.id }.toSet().size)
    }

    @Test
    fun `every stratum is represented at the reviewed distribution`() {
        val counts = cases.groupingBy { it.groundTruth.stratum() }.eachCount()
        assertEquals(67, counts[Stratum.BLATANT_SCAM])
        assertEquals(58, counts[Stratum.HARD_SUBTLE_SCAM])
        assertEquals(30, counts[Stratum.BLATANT_BENIGN])
        assertEquals(29, counts[Stratum.HARD_SUBTLE_BENIGN])
    }

    @Test
    fun `non-English share is at least 30 percent`() {
        val nonEnglish = cases.count { it.language != "en" }
        assertTrue(nonEnglish.toDouble() / cases.size >= 0.30)
    }
}
