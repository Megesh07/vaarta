package ai.vaarta

import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.Source

/**
 * One entry in the live WhatsApp-style thread (Phase 4A). The thread interleaves three voices so the
 * user can follow the call and the AI has the full back-and-forth as context (ADR-0003 attribution):
 * - [Caller] — the scammer's words (left bubble), from the mic transcript.
 * - [You] — the user's own spoken words (right bubble), attributed to the user because they matched a
 *   reply VAARTA suggested (deterministic `OwnWordsGate`) or the coach labelled them USER by content.
 * - [Coach] — VAARTA's recommendation (right, highlighted "say this"): a warning + graded replies,
 *   plus the web-grounded scam-ID when source-backed.
 *
 * Speaker attribution is best-effort (one mic can't hardware-separate voices); the deterministic score
 * never depends on it, and the hybrid alert is upward-only, so a misattribution can never lower
 * protection (ADR-0003).
 */
sealed interface ChatItem {
    data class Caller(val text: String) : ChatItem
    data class You(val text: String) : ChatItem
    data class Coach(
        val warning: String,
        val replies: List<Reply>,
        val scamType: String? = null,
        val sources: List<Source> = emptyList(),
    ) : ChatItem
}
