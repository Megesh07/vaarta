package ai.vaarta.core.reasoning

import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage

/** Risk states — thresholds 25/50/75 per MOBILE_UX_SPEC.md §2. */
enum class RiskLevel(val minScore: Int) {
    OBSERVING(0),
    CAUTION(25),
    HIGH_RISK(50),
    SCAM_PATTERN(75),
}

/** A signal that is currently contributing to the score, for the "why" line (UX §3.2). */
data class FiredSignal(
    val signalId: String,
    val category: SignalCategory,
    val stage: Stage,
    val contribution: Int,
    val explain: String?,
    val atMs: Long,
)

/** The engine's output after each event — deterministic and fully explainable. */
data class RiskState(
    val score: Int,
    val level: RiskLevel,
    val stage: Stage,
    val topSignals: List<FiredSignal>,
)
