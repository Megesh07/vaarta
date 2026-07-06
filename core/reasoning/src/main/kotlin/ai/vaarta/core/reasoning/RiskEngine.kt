package ai.vaarta.core.reasoning

import ai.vaarta.core.common.IntelPack
import ai.vaarta.core.common.Normalization
import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Signal
import ai.vaarta.core.common.Stage
import kotlin.math.pow

/**
 * Tunable constants for the deterministic engine. Provisional until weight-learning in M2
 * (AI_REASONING_ENGINE.md §10); every value here is inspectable, never a black box (guardrail
 * ALWAYS #5).
 */
data class EngineConfig(
    val hysteresisDownMs: Long = 90_000,
    val lowConfThreshold: Float = 0.6f,
    val lowConfMultiplier: Double = 0.5,
    val contactOffset: Int = 15,
    val extractionFloor: Int = 75,
    val stagePresenceMinContribution: Double = 1.0,
)

/**
 * The Tier-0 deterministic weighted-signal engine — AI_REASONING_ENGINE.md §1, §4.
 *
 * Deterministic, explainable, offline. Answers "how risky is this call right now?" by summing
 * decaying signal weights, rewarding scam-script *progression* through the stage grammar
 * (HOOK -> AUTHORITY -> ISOLATION -> ESCALATION -> EXTRACTION), and smoothing the displayed
 * risk level with hysteresis so the shield never flickers green because the scammer paused (§4.4).
 *
 * No LLM is ever in this path (guardrail NEVER #3). One instance == one call session; all state
 * is in RAM and is discarded when the instance is dropped (DATABASE_DESIGN.md §2).
 */
class RiskEngine(
    private val pack: IntelPack,
    private val activeLanguages: List<String>,
    private val config: EngineConfig = EngineConfig(),
) {
    companion object {
        /** UserAction that means "I know this caller" — disables scoring (§4.1). */
        const val ACTION_KNOWN_CALLER = "known_caller"
    }

    private class HitState(val signal: Signal, var lastHitMs: Long, var confMultiplier: Double)

    private val hits = LinkedHashMap<String, HitState>()
    private var benignOffset = 0
    private var scoringDisabled = false
    private var extractionSeen = false

    private var displayedLevel = RiskLevel.OBSERVING
    private var pendingDownSince: Long? = null

    /** Feed one event; returns the updated risk state. */
    fun ingest(event: RiskEvent): RiskState {
        when (event) {
            is RiskEvent.CallMeta ->
                if (event.isContact) benignOffset = maxOf(benignOffset, config.contactOffset)
            is RiskEvent.UserAction ->
                if (event.action == ACTION_KNOWN_CALLER) scoringDisabled = true
            is RiskEvent.ManualCue -> registerCue(event)
            is RiskEvent.Transcript -> registerTranscript(event)
        }
        return computeState(event.atMs)
    }

    private fun registerTranscript(e: RiskEvent.Transcript) {
        if (e.text.isBlank()) return
        val text = Normalization.normalize(e.text)
        val confMult = if (e.confidence < config.lowConfThreshold) config.lowConfMultiplier else 1.0
        for (signal in pack.signals) {
            if (signal.userSafe) continue // no diarization (§4.3): drop user-ambiguous signals
            if (TextMatcher.matches(text, signal, activeLanguages)) registerHit(signal, e.atMs, confMult)
        }
    }

    private fun registerCue(e: RiskEvent.ManualCue) {
        for (signal in pack.signals) {
            if (signal.manualCue == e.cueId) registerHit(signal, e.atMs, 1.0)
        }
    }

    private fun registerHit(signal: Signal, atMs: Long, confMult: Double) {
        val existing = hits[signal.id]
        if (existing != null) {
            // Refractory: ignore repeats within the window; otherwise refresh (resets decay clock).
            if (atMs - existing.lastHitMs < signal.refractoryS * 1000L) return
            existing.lastHitMs = atMs
            existing.confMultiplier = confMult
        } else {
            hits[signal.id] = HitState(signal, atMs, confMult)
        }
        if (signal.stage == Stage.EXTRACTION) extractionSeen = true
    }

    private fun decay(dtMs: Long, decayS: Int): Double =
        if (dtMs <= 0) 1.0 else 0.5.pow((dtMs / 1000.0) / decayS.toDouble())

    private fun computeState(nowMs: Long): RiskState {
        if (scoringDisabled) {
            displayedLevel = RiskLevel.OBSERVING
            return RiskState(0, RiskLevel.OBSERVING, Stage.NONE, emptyList())
        }

        val fired = ArrayList<FiredSignal>()
        val stagesPresent = sortedSetOf<Stage>()
        var sum = 0.0
        for (h in hits.values) {
            val contribution = h.signal.weight * h.confMultiplier * decay(nowMs - h.lastHitMs, h.signal.decayS)
            sum += contribution
            if (contribution >= config.stagePresenceMinContribution) {
                stagesPresent.add(h.signal.stage)
                fired += FiredSignal(
                    signalId = h.signal.id,
                    category = h.signal.category,
                    stage = h.signal.stage,
                    contribution = contribution.toInt(),
                    explain = explainFor(h.signal),
                    atMs = h.lastHitMs,
                )
            }
        }

        var raw = sum + comboBonus(stagesPresent) - benignOffset
        if (extractionSeen) raw = maxOf(raw, config.extractionFloor.toDouble())
        val score = raw.coerceIn(0.0, 100.0).toInt()

        val level = applyHysteresis(levelOf(score), nowMs)
        val highestStage = stagesPresent.maxByOrNull { it.ordinal } ?: Stage.NONE
        val top = fired.sortedByDescending { it.contribution }.take(3)
        return RiskState(score, level, highestStage, top)
    }

    /**
     * Stage-grammar bonus (§4.2) — the false-positive killer. Rewards *progression* that single
     * keywords cannot fake: a real bank fraud desk triggers AUTHORITY words but never reaches
     * ISOLATION + EXTRACTION.
     */
    private fun comboBonus(stages: Set<Stage>): Int {
        val scriptStages = stages.count { it != Stage.NONE }
        var bonus = when {
            scriptStages >= 3 -> 20
            scriptStages >= 2 -> 10
            else -> 0
        }
        if (Stage.ISOLATION in stages && Stage.ESCALATION in stages) bonus += 15 // the signature pair
        return bonus
    }

    private fun levelOf(score: Int): RiskLevel = when {
        score >= RiskLevel.SCAM_PATTERN.minScore -> RiskLevel.SCAM_PATTERN
        score >= RiskLevel.HIGH_RISK.minScore -> RiskLevel.HIGH_RISK
        score >= RiskLevel.CAUTION.minScore -> RiskLevel.CAUTION
        else -> RiskLevel.OBSERVING
    }

    /** Upward transitions are immediate; a downgrade requires the score to hold lower for 90 s (§4.4). */
    private fun applyHysteresis(target: RiskLevel, nowMs: Long): RiskLevel {
        when {
            target.ordinal > displayedLevel.ordinal -> {
                displayedLevel = target
                pendingDownSince = null
            }
            target.ordinal < displayedLevel.ordinal -> {
                val since = pendingDownSince
                if (since == null) {
                    pendingDownSince = nowMs
                } else if (nowMs - since >= config.hysteresisDownMs) {
                    displayedLevel = RiskLevel.entries[displayedLevel.ordinal - 1]
                    pendingDownSince = if (target.ordinal < displayedLevel.ordinal) nowMs else null
                }
            }
            else -> pendingDownSince = null
        }
        return displayedLevel
    }

    /**
     * Every signal that fired at least once this session (regardless of current decay), ordered by
     * time — the input for post-call complaint assembly (AI_REASONING_ENGINE.md §6.1).
     */
    fun sessionSignals(): List<FiredSignal> =
        hits.values
            .map { h -> FiredSignal(h.signal.id, h.signal.category, h.signal.stage, h.signal.weight, explainFor(h.signal), h.lastHitMs) }
            .sortedBy { it.atMs }

    private fun explainFor(signal: Signal): String? {
        for (lang in activeLanguages) signal.explain[lang]?.let { return it }
        return signal.explain.values.firstOrNull()
    }
}
