package ai.vaarta.core.reasoning

import ai.vaarta.core.common.Normalization
import ai.vaarta.core.common.Stage

/**
 * Deterministic "one step ahead" helper for the copilot (ADR-0003): the next stage in the fixed
 * HOOK→AUTHORITY→ISOLATION→ESCALATION→EXTRACTION script grammar (SCAM_INTELLIGENCE.md §4). This is
 * a plain ordinal lookup, not a prediction model — it lets the coach prompt say "he'll likely push
 * you to WhatsApp next" using the same deterministic grammar the risk engine already scores against,
 * so the copilot's foresight stays inspectable rather than an LLM guess. EXTRACTION has no next stage
 * (it's the end of the script); NONE's next stage is HOOK (the call hasn't started escalating yet).
 */
fun nextStage(current: Stage): Stage {
    val values = Stage.entries
    val next = current.ordinal + 1
    return if (next >= values.size) Stage.EXTRACTION else values[next]
}

/**
 * Assembles streamed transcription fragments into one readable turn (Phase 4A readability fix).
 * Gemini streams transcription as deltas whose own boundary spaces are significant ("what is" +
 * " your name"), so fragments must be concatenated WITHOUT per-fragment trimming — trimming each one
 * deletes the inter-word spaces and jams "what is your name" into "whatisyourname". Trim exactly ONCE,
 * on the assembled whole. This encodes the discipline both [ai.vaarta.ai.GeminiLiveClient] and the
 * ViewModel's fragment coalescing must follow.
 */
fun assembleTranscript(fragments: List<String>): String = fragments.joinToString(separator = "").trim()

/**
 * Deterministic, on-device defense against the self-echo feedback loop (ADR-0003, problem 1): on a
 * single speakerphone mic, the mic hears the user's own read-aloud coaching replies as well as the
 * caller's words, and Gemini's live transcription carries no speaker label. Without this gate, a user
 * reading back "I will not transfer the money" would be scored as if the SCAMMER said it — and
 * because that phrase contains "transfer the money" it can permanently floor [RiskEngine]'s score at
 * SCAM_PATTERN (the extraction floor never releases once seen).
 *
 * This is deliberately NOT the same mechanism as [ai.vaarta.core.common.Signal.userSafe]. `userSafe`
 * statically excludes a pack signal from scoring forever, regardless of who says it — appropriate
 * only for phrases that are inherently user-only (the docs' example is the "1930" helpline number).
 * Marking an existing strong scam signal like "police"/"transfer the money" `userSafe: true` would
 * blind the engine to a real scammer saying the exact same words — those are two of the highest-
 * value signals in the pack. This gate instead asks a narrower, dynamic question: "did VAARTA itself
 * just show this exact text?" — so real caller speech that happens to share vocabulary with a
 * suggestion (e.g. the caller also says "police") is untouched, and only near-exact echoes of what
 * was just displayed are attributed to the user.
 *
 * Best-effort, not perfect diarization (no hardware speaker separation exists on one mic,
 * AUDIO_PIPELINE.md §4) — it catches the highest-value case, not every case.
 */
class OwnWordsGate(private val rememberCount: Int = 6, private val maxEditRatio: Double = 0.3) {

    private val recentlyShown = ArrayDeque<String>()

    /** Call every time a question or coaching reply is shown to the user. */
    fun remember(shownText: String) {
        val normalized = Normalization.normalize(shownText)
        if (normalized.isBlank()) return
        recentlyShown.addLast(normalized)
        while (recentlyShown.size > rememberCount) recentlyShown.removeFirst()
    }

    /**
     * True if [heardText] is close enough to something VAARTA recently displayed that it is more
     * likely the user reading it aloud than the caller's independent speech. Compares on the
     * whitespace-stripped text via Levenshtein distance (same technique as [TextMatcher], which
     * tolerates the ASR noise inherent to the loudspeaker-through-air path) scaled to length so both
     * short and long lines are judged fairly.
     */
    fun isLikelyOwnWords(heardText: String): Boolean {
        val heard = Normalization.normalize(heardText).replace(" ", "")
        if (heard.isEmpty() || recentlyShown.isEmpty()) return false
        for (shown in recentlyShown) {
            val candidate = shown.replace(" ", "")
            if (candidate.isEmpty()) continue
            val dist = TextMatcher.levenshtein(heard, candidate)
            val maxLen = maxOf(heard.length, candidate.length)
            if (dist.toDouble() / maxLen <= maxEditRatio) return true
        }
        return false
    }

    fun reset() = recentlyShown.clear()
}
