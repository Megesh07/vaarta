package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class StratumTest {
    @Test
    fun `stratum maps all four isScam times difficulty combinations correctly`() {
        assertEquals(
            Stratum.BLATANT_SCAM,
            GroundTruth(isScam = true, variant = "digital-arrest", concern = Concern.SCAM_PATTERN, difficulty = Difficulty.BLATANT).stratum(),
        )
        assertEquals(
            Stratum.HARD_SUBTLE_SCAM,
            GroundTruth(isScam = true, variant = "digital-arrest", concern = Concern.CAUTION, difficulty = Difficulty.HARD_SUBTLE).stratum(),
        )
        assertEquals(
            Stratum.BLATANT_BENIGN,
            GroundTruth(isScam = false, variant = "", concern = Concern.OBSERVING, difficulty = Difficulty.BLATANT).stratum(),
        )
        assertEquals(
            Stratum.HARD_SUBTLE_BENIGN,
            GroundTruth(isScam = false, variant = "", concern = Concern.OBSERVING, difficulty = Difficulty.HARD_SUBTLE).stratum(),
        )
    }
}
