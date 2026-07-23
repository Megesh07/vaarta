package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class CaseLoaderTest {
    private val json = this::class.java.getResource("/tiny-cases.json")!!.readText()

    @Test
    fun `loads all cases with parsed ground truth`() {
        val cases = CaseLoader.load(json)
        assertEquals(2, cases.size)
        val scam = cases.first { it.id == "da-001" }
        assertTrue(scam.groundTruth.isScam)
        assertEquals(Concern.SCAM_PATTERN, scam.groundTruth.concern)
        assertEquals("digital-arrest", scam.groundTruth.variant)
        assertEquals(Difficulty.BLATANT, scam.groundTruth.difficulty)
        assertEquals(Provenance.SYNTHETIC, scam.provenance)
        val benign = cases.first { it.id == "benign-001" }
        assertEquals(false, benign.groundTruth.isScam)
        assertEquals(Concern.OBSERVING, benign.groundTruth.concern)
        assertEquals(Difficulty.BLATANT, benign.groundTruth.difficulty)
        assertEquals(Provenance.SYNTHETIC, benign.provenance)
    }
}
