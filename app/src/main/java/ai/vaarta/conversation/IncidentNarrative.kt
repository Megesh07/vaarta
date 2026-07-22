package ai.vaarta.conversation

import ai.vaarta.ChatItem

/**
 * Pure transcript builder for [ConversationViewModel.buildComplaintDraft] — extracted so the
 * Caller/You-only filtering (the 2026-07-22 fix that stopped VAARTA's own Assistant/Coach replies,
 * including fail-closed apology text, from leaking into a real incident description) is unit-testable
 * without an [android.app.Application]/[androidx.lifecycle.AndroidViewModel] in the loop.
 */
internal fun conversationTranscript(items: List<ChatItem>): String = items.mapNotNull { item ->
    when (item) {
        is ChatItem.Caller -> "Caller: ${item.text}"
        is ChatItem.You -> "Me: ${item.text}"
        is ChatItem.Coach, is ChatItem.Assistant -> null
    }
}.joinToString("\n")

/** Prepends the verdict line (when a header exists) to [transcript] for the incident narrative. */
internal fun incidentNarrativeText(header: ConversationViewModel.CallHeader?, transcript: String): String = buildString {
    header?.let {
        append("VAARTA's verdict: ${it.level.name} (risk ${it.score}/100)")
        it.scamType?.let { st -> append(", identified as \"$st\"") }
        append(".\n\n")
    }
    append(transcript)
}
