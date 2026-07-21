package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Panic-sheet context selection (redesign spec §A2): live call > recent call > nothing. */
class PanicContextSelectorTest {

    @Test
    fun `prefers a live call with a detected scam type`() {
        val line = PanicContextSelector.select(
            liveScamType = "Digital arrest",
            liveRiskLevel = "SCAM_PATTERN",
            recentScamType = "Loan app harassment",
            recentRiskLevel = "HIGH_RISK",
        )
        assertTrue(line!!.contains("Digital arrest"))
        assertTrue(line.contains("SCAM_PATTERN"))
        assertTrue(line.contains("live call"))
    }

    @Test
    fun `a live call with raised risk but no named scam type still counts as active`() {
        val line = PanicContextSelector.select(
            liveScamType = null,
            liveRiskLevel = "HIGH_RISK",
            recentScamType = null,
            recentRiskLevel = null,
        )
        assertTrue(line!!.contains("not yet identified"))
        assertTrue(line.contains("HIGH_RISK"))
    }

    @Test
    fun `an observing live call with no scam type falls through to the recent call`() {
        val line = PanicContextSelector.select(
            liveScamType = null,
            liveRiskLevel = "OBSERVING",
            recentScamType = "Courier customs scam",
            recentRiskLevel = "SCAM_PATTERN",
        )
        assertTrue(line!!.contains("Courier customs scam"))
        assertTrue(line.contains("most recently analyzed"))
    }

    @Test
    fun `a recent call with raised risk but no scam type still counts as active`() {
        val line = PanicContextSelector.select(
            liveScamType = null,
            liveRiskLevel = "OBSERVING",
            recentScamType = null,
            recentRiskLevel = "CAUTION",
        )
        assertTrue(line!!.contains("a suspicious pattern"))
        assertTrue(line.contains("CAUTION"))
    }

    @Test
    fun `nothing known returns null so the caller skips the AI call entirely`() {
        val line = PanicContextSelector.select(
            liveScamType = null,
            liveRiskLevel = "OBSERVING",
            recentScamType = null,
            recentRiskLevel = null,
        )
        assertNull(line)
    }

    @Test
    fun `an observing recent call with no scam type is not active`() {
        val line = PanicContextSelector.select(
            liveScamType = null,
            liveRiskLevel = "OBSERVING",
            recentScamType = null,
            recentRiskLevel = "OBSERVING",
        )
        assertNull(line)
    }
}
