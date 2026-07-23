package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ScorerTest {
    private fun scamCase(id: String, variant: String) =
        TranscriptCase(id, "t", "en", GroundTruth(true, variant, Concern.SCAM_PATTERN), "s")
    private fun benignCase(id: String) =
        TranscriptCase(id, "t", "en", GroundTruth(false, "", Concern.OBSERVING), "s")
    private fun verdict(isScam: Boolean?, concern: Concern?, variant: String, latency: Long, error: String? = null) =
        Verdict("j", isScam, concern, variant, 0.9, latency, "{}", error)

    @Test
    fun `computes false-safe, false-alarm, recall, variant accuracy`() {
        val pairs = listOf(
            scamCase("s1", "digital-arrest") to verdict(true, Concern.SCAM_PATTERN, "digital-arrest", 100),  // caught, variant right
            scamCase("s2", "digital-arrest") to verdict(false, Concern.OBSERVING, "", 120),                  // MISSED -> false-safe
            scamCase("s3", "upi-refund")     to verdict(null, null, "", 0, error = "timeout"),               // error on scam -> false-safe
            benignCase("b1")                 to verdict(true, Concern.HIGH_RISK, "digital-arrest", 90),       // false alarm
            benignCase("b2")                 to verdict(false, Concern.OBSERVING, "", 80),                    // correct benign
        )
        val r = Scorer.score("j", pairs)
        assertEquals(5, r.total)
        assertEquals(2, r.falseSafe)                                    // s2 + s3
        assertEquals(1, r.falseAlarm)                                   // b1
        assertEquals(1.0 / 3.0, r.recall, 1e-9)                         // 1 of 3 scams caught
        assertEquals(1.0, r.variantAccuracy, 1e-9)                      // the one caught scam had the right variant
    }
}
