package ai.vaarta.tools.eval

/**
 * Aggregate metrics for one judge run over a set of (case, verdict) pairs.
 *
 * [falseSafeRate] is the headline number: the fraction of real scams the judge wrongly
 * called safe (including judge-call errors on scam cases — we fail closed, never lenient).
 * Every future benchmark comparison should be read through this number first.
 */
data class Report(
    val judgeId: String,
    val total: Int,
    val scored: Int,
    val errors: Int,
    val falseSafe: Int,
    val falseSafeRate: Double,
    val falseAlarm: Int,
    val falseAlarmRate: Double,
    val recall: Double,
    val variantAccuracy: Double,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
)

object Scorer {
    /**
     * A verdict counts as flagging a scam if it explicitly says so or rates the risk high.
     * A failed judge call (`error != null`) is NEVER scam-positive — a call that errored
     * out must never be scored as "the model said this was safe".
     */
    private fun isScamPositive(v: Verdict): Boolean =
        v.error == null && (v.isScam == true || v.concern == Concern.HIGH_RISK || v.concern == Concern.SCAM_PATTERN)

    fun score(judgeId: String, pairs: List<Pair<TranscriptCase, Verdict>>): Report {
        val scamCases = pairs.filter { it.first.groundTruth.isScam }
        val benignCases = pairs.filter { !it.first.groundTruth.isScam }

        // False-safe: ground truth is scam, but the verdict is NOT scam-positive.
        // Errors are folded in via isScamPositive (fail closed: an errored call on a scam
        // case is a miss, never "safe").
        val falseSafe = scamCases.count { (_, v) -> !isScamPositive(v) }

        // False-alarm: ground truth benign, but the verdict IS scam-positive.
        val falseAlarm = benignCases.count { (_, v) -> isScamPositive(v) }

        val caughtScams = scamCases.filter { (_, v) -> isScamPositive(v) }
        val variantHits = caughtScams.count { (c, v) ->
            v.variant.trim().equals(c.groundTruth.variant.trim(), ignoreCase = true)
        }

        val latencies = pairs.mapNotNull { (_, v) -> v.latencyMs.takeIf { v.error == null } }.sorted()
        fun percentile(p: Double): Long =
            if (latencies.isEmpty()) 0L
            else latencies[(Math.ceil(p * latencies.size) - 1).toInt().coerceIn(0, latencies.size - 1)]

        return Report(
            judgeId = judgeId,
            total = pairs.size,
            scored = pairs.count { it.second.error == null },
            errors = pairs.count { it.second.error != null },
            falseSafe = falseSafe,
            falseSafeRate = if (scamCases.isEmpty()) 0.0 else falseSafe.toDouble() / scamCases.size,
            falseAlarm = falseAlarm,
            falseAlarmRate = if (benignCases.isEmpty()) 0.0 else falseAlarm.toDouble() / benignCases.size,
            recall = if (scamCases.isEmpty()) 0.0 else caughtScams.size.toDouble() / scamCases.size,
            variantAccuracy = if (caughtScams.isEmpty()) 0.0 else variantHits.toDouble() / caughtScams.size,
            p50LatencyMs = percentile(0.50),
            p95LatencyMs = percentile(0.95),
        )
    }
}
