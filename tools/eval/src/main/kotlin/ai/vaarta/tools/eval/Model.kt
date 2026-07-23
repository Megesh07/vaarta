package ai.vaarta.tools.eval

enum class Concern { OBSERVING, CAUTION, HIGH_RISK, SCAM_PATTERN }

data class GroundTruth(val isScam: Boolean, val variant: String, val concern: Concern)

data class TranscriptCase(
    val id: String,
    val transcript: String,
    val language: String,
    val groundTruth: GroundTruth,
    val source: String,
)

/** A judge's answer for one case. Nulls mean "the model didn't produce this field"; a non-null
 *  [error] means the call failed and the case must NOT be scored as benign (fail closed). */
data class Verdict(
    val judgeId: String,
    val isScam: Boolean?,
    val concern: Concern?,
    val variant: String,
    val confidence: Double?,
    val latencyMs: Long,
    val raw: String,
    val error: String?,
)
