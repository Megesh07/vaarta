package ai.vaarta.core.complaint

import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage
import kotlinx.serialization.Serializable

/**
 * The structured complaint — export interchange schema `vaarta.complaint.v1` (DATABASE_DESIGN.md §5).
 * PDF / DOCX / TXT / JSON are all renderings of this one object (one source of truth, N renderers).
 */
@Serializable
data class ComplaintDraft(
    val schema: String = "vaarta.complaint.v1",
    val generatedAtIso: String,
    val incident: Incident,
    val classification: Classification,
    val complainant: Complainant?,
    val loss: Loss?,
    val narrative: Narrative,
    val evidence: List<EvidenceItem>,
    val disclaimer: String,
    /** Per-slot provenance for the "verify before filing" markers. */
    val slotsMeta: Map<String, SlotSource>,
)

@Serializable
data class Incident(
    val startIso: String,
    val endIso: String,
    val durationMinutes: Long,
    val callerNumbers: List<String>,
    val platforms: List<String>,
)

@Serializable
data class Classification(
    val scamCode: String?,
    val scamName: String?,
    val confidenceBucket: String,
    val topSignals: List<ClassifiedSignal>,
)

@Serializable
data class ClassifiedSignal(
    val signalId: String,
    val category: SignalCategory,
    val stage: Stage,
    val atOffsetMs: Long,
    val explain: String?,
    val quote: String?,
)

@Serializable
data class Complainant(val name: String?, val address: String?)

@Serializable
data class Loss(val amountInr: Long?, val transactionRefs: List<String>)

@Serializable
data class Narrative(val lang: String, val text: String)

@Serializable
data class EvidenceItem(val atOffsetMs: Long, val description: String, val quote: String?)
