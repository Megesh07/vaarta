package ai.vaarta.core.complaint

import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplaintBuilderTest {

    // Fixed epochs so the whole draft is deterministic (no wall-clock).
    private val start = 1_720_000_000_000L
    private val end = start + 180_000L // +3 min
    private val generatedAt = end + 20_000L

    private fun sampleInput() = ComplaintInput(
        callerNumber = "+91 92XXXXXX21",
        callStartEpochMs = start,
        callEndEpochMs = end,
        languages = listOf("en", "hi"),
        matchedScamCode = "SC-01",
        matchedScamName = "Digital Arrest — CBI impersonation",
        finalScore = 88,
        detectedSignals = listOf(
            DetectedSignal("SIG_AUTHORITY_CBI", SignalCategory.AUTHORITY_CLAIM, Stage.AUTHORITY, 30_000, "Caller claims to be from the CBI"),
            DetectedSignal("SIG_ISOLATION_SECRECY", SignalCategory.ISOLATION_ORDER, Stage.ISOLATION, 75_000, "Ordered to keep it secret from family"),
            DetectedSignal("SIG_EXTRACTION_TRANSFER", SignalCategory.EXTRACTION_MOVE, Stage.EXTRACTION, 155_000, "Demanded money transfer to an 'RBI account'"),
        ),
        lossAmountInr = 250_000,
        complainantName = "R. Kumar",
    )

    @Test
    fun `assembles a classified, high-confidence draft`() {
        val d = ComplaintBuilder.assemble(sampleInput(), generatedAt)
        assertEquals("HIGH", d.classification.confidenceBucket)
        assertEquals(3, d.classification.topSignals.size)
        assertEquals(3L, d.incident.durationMinutes)
        assertEquals(SlotSource.USER, d.slotsMeta["loss.amountInr"])
        assertEquals(SlotSource.DETECTED, d.slotsMeta["classification.scamName"])
    }

    @Test
    fun `narrative is deterministic and cites detected signals`() {
        val d = ComplaintBuilder.assemble(sampleInput(), generatedAt)
        assertTrue(d.narrative.text.contains("CBI"))
        assertTrue(d.narrative.text.contains("Digital Arrest"))
        assertTrue(d.narrative.text.contains("250000"))
    }

    @Test
    fun `json export round-trips`() {
        val d = ComplaintBuilder.assemble(sampleInput(), generatedAt)
        val jsonText = ComplaintRenderers.toJson(d)
        val back = Json { ignoreUnknownKeys = true }.decodeFromString(ComplaintDraft.serializer(), jsonText)
        assertEquals(d, back)
        assertTrue(jsonText.contains("vaarta.complaint.v1"))
    }

    @Test
    fun `text export contains disclaimer and caller number`() {
        val txt = ComplaintRenderers.toText(ComplaintBuilder.assemble(sampleInput(), generatedAt))
        assertTrue(txt.contains("+91 92XXXXXX21"))
        assertTrue(txt.contains("verify all details before filing"))
    }
}
