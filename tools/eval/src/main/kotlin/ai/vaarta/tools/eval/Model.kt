package ai.vaarta.tools.eval

enum class Concern { OBSERVING, CAUTION, HIGH_RISK, SCAM_PATTERN }

/** BLATANT = an obvious case either way (clear scam script, or clearly ordinary call).
 *  HARD_SUBTLE = a scam with no obvious trigger words, OR a benign call that superficially
 *  sounds scary — the stratum this whole scoring scheme exists to stop a model from hiding
 *  weakness behind (owner directive: be strict, never blend easy and hard cases together). */
enum class Difficulty { BLATANT, HARD_SUBTLE }

/** Where a case's transcript came from — recorded so scoring can be read per-source and any
 *  synthetic bias is visible, not hidden (spec §9 anti-inflation guardrail). */
enum class Provenance { REAL_DERIVED, SYNTHETIC, PUBLIC_DATASET }

/** The four buckets every metric must be computed separately for. Deliberately NOT a single
 *  blended dimension — a model must clear the bar on HARD_SUBTLE_SCAM and HARD_SUBTLE_BENIGN
 *  specifically, not just on average. */
enum class Stratum { BLATANT_SCAM, HARD_SUBTLE_SCAM, BLATANT_BENIGN, HARD_SUBTLE_BENIGN }

data class GroundTruth(
    val isScam: Boolean,
    val variant: String,
    val concern: Concern,
    val difficulty: Difficulty,
)

/** Pure: which of the four strata this ground truth belongs to. */
fun GroundTruth.stratum(): Stratum = when {
    isScam && difficulty == Difficulty.BLATANT -> Stratum.BLATANT_SCAM
    isScam && difficulty == Difficulty.HARD_SUBTLE -> Stratum.HARD_SUBTLE_SCAM
    !isScam && difficulty == Difficulty.BLATANT -> Stratum.BLATANT_BENIGN
    else -> Stratum.HARD_SUBTLE_BENIGN
}

data class TranscriptCase(
    val id: String,
    val transcript: String,
    val language: String,
    val groundTruth: GroundTruth,
    val source: String,
    val provenance: Provenance,
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
