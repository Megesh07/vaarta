package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage
import ai.vaarta.core.reasoning.ComplaintPlaybookLoader
import ai.vaarta.core.reasoning.ComplaintRouter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplaintPacketAssemblerTest {

    private fun draft() = ComplaintBuilder.assemble(
        ComplaintInput(
            callerNumber = "+919812345678",
            callStartEpochMs = 1_720_000_000_000L,
            callEndEpochMs = 1_720_000_180_000L,
            languages = listOf("en"),
            matchedScamCode = "SC-01",
            matchedScamName = "Digital Arrest - police/CBI impersonation",
            finalScore = 100,
            detectedSignals = listOf(
                DetectedSignal("SIG_LEGAL_THREAT", SignalCategory.LEGAL_THREAT, Stage.AUTHORITY, 5_000, "arrest threat"),
            ),
        ),
        generatedAtEpochMs = 1_720_000_200_000L,
    )

    private val ncrp = ComplaintRouter.route(ComplaintPlaybookLoader.bundled(), "SC-01", moneyLost = true).first()

    @Test
    fun `narrative field is filled from the draft and clears the min-char floor`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        val desc = packet.fields.first { it.key == "incident.description" }
        assertTrue(desc.value.length >= 200, "narrative too short for portal floor")
    }

    @Test
    fun `caller number maps to suspect field`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertEquals("+919812345678", packet.fields.first { it.key == "suspect.mobile" }.value)
    }

    @Test
    fun `identity fills complainant fields when present`() {
        val id = IdentityDetails("Asha", "12 MG Road", "+919000000000", "a@b.in", "PAN")
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = id, loss = null)
        assertEquals("Asha", packet.fields.first { it.key == "complainant.name" }.value)
    }

    @Test
    fun `checklist marks transcript as provided and ID proof as user-supplied`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertTrue(packet.checklist.first { it.label.contains("transcript", true) }.providedByVaarta)
        assertTrue(packet.checklist.any { !it.providedByVaarta })
    }

    @Test
    fun `procedure steps carry the you-only marker text`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertTrue(packet.procedureSteps.any { it.contains("(you)") })
    }
}
