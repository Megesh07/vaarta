package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ComplaintPlaybookParityTest {

    private val validSlots = setOf(
        "narrative", "callerNumber", "category", "incidentDate", "platform",
        "identity.name", "identity.address", "identity.mobile", "identity.email",
        "loss.amount", "loss.txnId", "loss.txnDate",
    )

    @Test
    fun `bundled playbook loads and every destination is well-formed`() {
        val pb = ComplaintPlaybookLoader.bundled()
        assertFalse(pb.destinations.isEmpty(), "playbook has no destinations")
        for (d in pb.destinations) {
            assertTrue(d.categoryValue.isNotBlank(), "${d.id} missing categoryValue")
            assertTrue(d.procedure.isNotEmpty(), "${d.id} has no procedure steps")
            assertTrue(d.scamCodes.isNotEmpty(), "${d.id} routes from no scam codes")
        }
        assertTrue(pb.verifiedOn.isNotBlank(), "playbook missing verifiedOn")
    }

    @Test
    fun `every mapped field slot is a known packet slot`() {
        val pb = ComplaintPlaybookLoader.bundled()
        val bad = pb.destinations.flatMap { it.fields }.map { it.slot }.filter { it !in validSlots }
        assertTrue(bad.isEmpty(), "unknown field slots (would silently fail autofill): $bad")
    }
}
