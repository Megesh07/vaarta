package ai.vaarta.core.common

/**
 * The engine's input event model — AI_REASONING_ENGINE.md §2.
 * All processing is per-session, in RAM; nothing here touches disk (DATABASE_DESIGN.md §2).
 *
 * [atMs] is the event time as a millisecond offset from the start of the call, so the
 * deterministic engine and its tests are wall-clock independent.
 */
sealed interface RiskEvent {
    val atMs: Long

    /** A partial or final ASR transcript segment. */
    data class Transcript(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val isFinal: Boolean,
        val langHint: String? = null,
        val confidence: Float = 1.0f,
    ) : RiskEvent {
        override val atMs: Long get() = endMs
    }

    /** A Manual Mode chip tap — the audio-free peer path (MOBILE_UX_SPEC.md §3.3, guardrail L2). */
    data class ManualCue(
        val cueId: String,
        override val atMs: Long,
    ) : RiskEvent

    /** Call metadata known at session start. */
    data class CallMeta(
        val number: String?,
        val isContact: Boolean,
        val callType: CallType,
        override val atMs: Long = 0L,
    ) : RiskEvent

    /** A user action during the session (enabled speaker, sent alert, marked caller as known). */
    data class UserAction(
        val action: String,
        override val atMs: Long,
    ) : RiskEvent
}

enum class CallType { INCOMING, OUTGOING, UNKNOWN }
