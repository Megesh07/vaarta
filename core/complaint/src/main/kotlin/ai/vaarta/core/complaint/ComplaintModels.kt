package ai.vaarta.core.complaint

import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage
import kotlinx.serialization.Serializable

/**
 * Input to the complaint builder — a decoupled view of a session (module rule: core:complaint
 * depends only on core:common, never on core:reasoning; the app maps engine output to this).
 */
data class ComplaintInput(
    val callerNumber: String?,
    val callStartEpochMs: Long,
    val callEndEpochMs: Long,
    val languages: List<String>,
    val matchedScamCode: String?,
    val matchedScamName: String?,
    val finalScore: Int,
    val detectedSignals: List<DetectedSignal>,
    // User-entered / editable fields:
    val complainantName: String? = null,
    val complainantAddress: String? = null,
    val lossAmountInr: Long? = null,
    val transactionRefs: List<String> = emptyList(),
)

data class DetectedSignal(
    val signalId: String,
    val category: SignalCategory,
    val stage: Stage,
    val atMs: Long,
    val explain: String?,
    val quote: String? = null,
)

/** Provenance of each generated field — surfaced as "auto-filled, verify" markers (UX §3.6). */
@Serializable
enum class SlotSource { DETECTED, USER, DEFAULT }
