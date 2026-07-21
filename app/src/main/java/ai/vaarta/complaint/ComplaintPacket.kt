package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.complaint.SlotSource
import ai.vaarta.core.reasoning.ComplaintDestination

/** Money the user reports lost — entered in the Review step (spec §5, NCRP path only). */
data class LossInput(val amountInr: Long?, val txnId: String?, val txnDate: String?)

data class FilledField(
    val key: String,
    val label: String,
    val value: String,
    val source: SlotSource, // DETECTED / USER / DEFAULT — drives the "auto-filled, verify" marker
    val selector: String?,
    val minChars: Int?,
)

data class ChecklistItem(val label: String, val providedByVaarta: Boolean, val note: String)

data class ComplaintPacket(
    val destinationId: String,
    val destinationName: String,
    val url: String,
    val phone: String?,
    val categoryValue: String,
    val fields: List<FilledField>,
    val checklist: List<ChecklistItem>,
    val procedureSteps: List<String>,
)

object ComplaintPacketAssembler {

    fun assemble(
        dest: ComplaintDestination,
        draft: ComplaintDraft,
        identity: IdentityDetails?,
        loss: LossInput?,
    ): ComplaintPacket {
        val platform = draft.incident.platforms.firstOrNull() ?: "Phone call"
        val fields = dest.fields.mapNotNull { f ->
            val (value, source) = when (f.slot) {
                "narrative" -> draft.narrative.text to SlotSource.DETECTED
                "callerNumber" -> (draft.incident.callerNumbers.firstOrNull() ?: "") to
                    (if (draft.incident.callerNumbers.isNotEmpty()) SlotSource.DETECTED else SlotSource.DEFAULT)
                "category" -> dest.categoryValue to SlotSource.DEFAULT
                "incidentDate" -> draft.incident.startIso to SlotSource.DETECTED
                "platform" -> platform to SlotSource.DETECTED
                "identity.name" -> (identity?.name ?: "") to SlotSource.USER
                "identity.address" -> (identity?.address ?: "") to SlotSource.USER
                "identity.mobile" -> (identity?.mobile ?: "") to SlotSource.USER
                "identity.email" -> (identity?.email ?: "") to SlotSource.USER
                "loss.amount" -> (loss?.amountInr?.toString() ?: "") to SlotSource.USER
                "loss.txnId" -> (loss?.txnId ?: "") to SlotSource.USER
                "loss.txnDate" -> (loss?.txnDate ?: "") to SlotSource.USER
                else -> return@mapNotNull null
            }
            FilledField(f.key, f.label, value, source, f.selector, f.minChars)
        }
        val checklist = dest.documents.map { ChecklistItem(it.label, it.providedByVaarta, it.note) }
        val steps = dest.procedure.sortedBy { it.order }
            .map { if (it.userOnly) "${it.text} (you)" else it.text }
        return ComplaintPacket(
            destinationId = dest.id,
            destinationName = dest.name,
            url = dest.url,
            phone = dest.phone,
            categoryValue = dest.categoryValue,
            fields = fields,
            checklist = checklist,
            procedureSteps = steps,
        )
    }
}
