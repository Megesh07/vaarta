package ai.vaarta.core.reasoning

import ai.vaarta.core.common.Stage

/**
 * Data models for the live conversation copilot (ADR-0003). These sit in `core:reasoning` so they
 * are pure-JVM unit-testable, exactly like [LiveSuggestion]. The wire/JSON parsing lives at the app
 * boundary ([ai.vaarta.ai.GeminiClient]); these are the validated domain shapes the rest of the app
 * consumes after [SuggestionSafetyFilter] has run.
 *
 * Score ownership is unchanged: none of this feeds the deterministic risk score (ADR-0002 D1). The
 * copilot only advises what the user can say.
 */

/**
 * Who spoke a given turn. Attribution is best-effort — there is NO hardware speaker diarization on a
 * single speakerphone mic (ADR-0003, AUDIO_PIPELINE.md §4). [CALLER]/[USER] may be inferred by the
 * LLM purely for the labelled chat display; the risk score never depends on this field.
 */
enum class Speaker { CALLER, USER, UNKNOWN;

    companion object {
        /** Tolerant mapping from the analyzer LLM's free-string speaker label (ADR-0003 Phase 4D).
         *  Anything unrecognized → [UNKNOWN], which the recording analyzer still feeds to the engine
         *  (a mislabel can only ADD scam signals, never remove protection). */
        fun fromWire(raw: String?): Speaker = when (raw?.trim()?.uppercase()) {
            "CALLER", "SCAMMER", "AGENT", "OFFICER", "THEM", "CALL", "A" -> CALLER
            "USER", "YOU", "VICTIM", "ME", "SELF", "B" -> USER
            else -> UNKNOWN
        }
    }
}

/**
 * The intent of a suggested reply. The copilot offers ONLY these three shapes — a calm verification
 * question, a firm refusal, or a safe exit. Anything the model returns that does not map to one of
 * these is dropped (positive-shape guard, ADR-0003) so a hijacked/hallucinated output cannot reach
 * the user as an unclassified instruction.
 */
enum class ReplyKind {
    /** A calm verification question the user asks to test the caller's legitimacy. */
    VERIFY,

    /** A firm boundary that refuses a demand (money, OTP, isolation) without escalating. */
    REFUSE,

    /** End the call safely — the right move once EXTRACTION is reached. */
    EXIT,
    ;

    companion object {
        /** Tolerant mapping from the model's free-string label. Unknown/blank → null (reply dropped). */
        fun fromWire(raw: String?): ReplyKind? = when (raw?.trim()?.lowercase()) {
            "verify", "verification", "question", "ask" -> VERIFY
            "refuse", "refusal", "boundary", "decline", "deny" -> REFUSE
            "exit", "hang up", "hangup", "end", "escape", "leave" -> EXIT
            else -> null
        }
    }
}

/** One coaching reply the user can read aloud. Never auto-played (ADR-0002 S8) — display only. */
data class Reply(val text: String, val kind: ReplyKind)

/** A cited web source backing a grounded scam-variant claim (ADR-0003). Title + URL from Gemini's
 *  grounding metadata. A [scamType]/[CoachingResponse.benign] claim without at least one source is
 *  dropped — this is what stops the AI hallucinating scam variants. */
data class Source(val title: String, val uri: String)

/**
 * The AI's coaching for one caller turn: a short plain-language warning (what's happening + what the
 * scammer likely wants next) plus up to a few graded replies. Produced by the cascaded text LLM,
 * then every reply and the warning pass [SuggestionSafetyFilter] before display.
 *
 * The web-grounded fields (ADR-0003 hybrid) are ADVISORY and default to "nothing extra": [concern]
 * is the AI's own risk read and may only ever RAISE the displayed alert above the deterministic
 * floor (never lower it — see [HybridAlert]); [scamType] + [sources] name the current scam variant
 * found on the live web (must be source-backed); [benign] is the AI's "this looks like a genuine
 * call" read, honoured only by cited consensus with the deterministic engine ([HybridAlert.mayReassure]).
 */
data class CoachingResponse(
    val warning: String,
    val replies: List<Reply>,
    val concern: RiskLevel = RiskLevel.OBSERVING,
    val scamType: String? = null,
    val sources: List<Source> = emptyList(),
    val benign: Boolean = false,
)

/** The AI's grounded classification of the call (ADR-0003 Call A). Kept separate from the coaching
 *  ([CoachingResponse] Call B) because they come from two different Gemini calls; the ViewModel
 *  merges them. [scamType]/[benign] are only trustworthy when [sources] is non-empty. */
data class GroundedAssessment(
    val concern: RiskLevel = RiskLevel.OBSERVING,
    val scamType: String? = null,
    val benign: Boolean = false,
    val sources: List<Source> = emptyList(),
)

/** One utterance in the running call transcript (RAM-only; discarded on session end, P-2). */
data class ConversationTurn(val speaker: Speaker, val text: String, val atMs: Long)

/** One diarized utterance from a recorded-call analysis (ADR-0003 Phase 4D). Speaker attribution is
 *  the analyzer LLM's best guess (no hardware diarization); the deterministic score never trusts it —
 *  see [ai.vaarta.AudioScamAnalyzer], which feeds caller-side text to the engine but treats a mislabel
 *  as fail-toward-scoring (adding a signal is safe; dropping a real caller line is the dangerous error). */
data class AudioTurn(val speaker: Speaker, val text: String)

/**
 * The analyzer LLM's read of a whole recorded call (ADR-0003 Phase 4D) — the recording counterpart to
 * the live [CoachingResponse]/[GroundedAssessment]. [concern] is ADVISORY exactly like
 * [CoachingResponse.concern]: it may only RAISE the displayed alert above the deterministic floor via
 * [HybridAlert], never lower it. [benign] is honoured only by cited consensus ([HybridAlert.mayReassure]).
 * The authoritative score still comes from replaying [turns] through the deterministic [RiskEngine].
 */
data class AudioAnalysis(
    val turns: List<AudioTurn>,
    val concern: RiskLevel = RiskLevel.OBSERVING,
    val summary: String = "",
    val benign: Boolean = false,
    val language: String? = null,
)

/**
 * One rendered entry in the copilot feed: what the caller said + the coaching shown for it, plus the
 * deterministic score/stage at that moment (the score is Tier-0's, shown alongside — not set by AI).
 * [scamType]/[sources] are the web-grounded variant identification for this turn, present only when
 * a source-backed classification was available (ADR-0003); empty otherwise.
 */
data class CoachTurn(
    val callerLine: String,
    val warning: String,
    val replies: List<Reply>,
    val stage: Stage,
    val score: Int,
    val scamType: String? = null,
    val sources: List<Source> = emptyList(),
)
