package ai.vaarta.core.common

import kotlinx.serialization.Serializable

/**
 * Intel-pack data model — AI_REASONING_ENGINE.md §3, SCAM_INTELLIGENCE.md §5.
 * Packs are DATA, not code: signals + questions authored per language, loaded at session start.
 * (v1 production compiles signed YAML -> binary; the MVP loads JSON — ADR-0001.)
 */
@Serializable
data class IntelPack(
    val packId: String,
    val version: String,
    val signals: List<Signal>,
    val questions: List<Question> = emptyList(),
)

/** One detectable signal. See AI_REASONING_ENGINE.md §3 for the field contract. */
@Serializable
data class Signal(
    val id: String,
    val category: SignalCategory,
    val stage: Stage,
    val weight: Int,
    /** Contribution halves after this many seconds without re-trigger. */
    val decayS: Int = 600,
    /** Repeated hits of the same signal count at most once per this window. */
    val refractoryS: Int = 120,
    /** Language key ("hi", "hi_latn", "en", "mix", …) -> phrase list. */
    val patterns: Map<String, List<String>>,
    val match: MatchMode = MatchMode.FUZZY1,
    /** Language key -> human-readable reason shown in the bubble / debrief. */
    val explain: Map<String, String> = emptyMap(),
    /** The Manual Mode cue that also fires this signal, if any (guardrail ALWAYS #10). */
    val manualCue: String? = null,
    /**
     * True when the phrase is scam-indicative only if the caller says it, and would misfire
     * if user-spoken (e.g. "1930"). Excluded from scoring — AI_REASONING_ENGINE.md §4.3.
     */
    val userSafe: Boolean = false,
)

/** The eleven canonical signal categories — SCAM_INTELLIGENCE.md §5 (+ BENIGN for negative offsets).
 *  FINANCIAL_LURE and SERVICE_THREAT added for pack v2 (2026-07-18): the "you will gain money"
 *  bait pattern (investment/job-task/loan-app/lottery/UPI-refund) and the "pay now or lose service"
 *  threat pattern (electricity disconnection) that digital-arrest's categories didn't cover. */
@Serializable
enum class SignalCategory {
    AUTHORITY_CLAIM, LEGAL_THREAT, ISOLATION_ORDER, CHANNEL_SWITCH,
    URGENCY_PRESSURE, IDENTITY_PHISH, EXTRACTION_MOVE, LEGITIMACY_THEATER,
    PARCEL_PRETEXT, FINANCIAL_LURE, SERVICE_THREAT, BENIGN,
}

/** The five-stage digital-arrest script grammar — SCAM_INTELLIGENCE.md §4. */
@Serializable
enum class Stage { NONE, HOOK, AUTHORITY, ISOLATION, ESCALATION, EXTRACTION }

@Serializable
enum class MatchMode { EXACT, STEM, FUZZY1, FUZZY2, REGEX }

/** A stage/category-keyed verification question — AI_REASONING_ENGINE.md §5. */
@Serializable
data class Question(
    val id: String,
    val stage: Stage,
    val category: SignalCategory,
    /** Language key -> question text (native and romanized carried as separate keys). */
    val text: Map<String, String>,
    val goal: String? = null,
)
