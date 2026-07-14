package ai.vaarta.core.reasoning

/**
 * Free-form "Ask VAARTA" chat (v2). One [ChatMessage] per turn; [fromUser] true = the user, false =
 * VAARTA. Kept in core:reasoning (pure) so request assembly + title derivation are unit-testable
 * without Android or the network.
 */
data class ChatMessage(val fromUser: Boolean, val text: String)

/** VAARTA's answer to a chat message: plain-language prose plus any cited web sources. */
data class ChatAnswer(val text: String, val sources: List<Source> = emptyList())

/**
 * Derives a short, human title for the Conversations list from the first user message. Pure:
 * collapses whitespace, trims, truncates with an ellipsis, and never returns blank.
 */
fun conversationTitleFrom(firstMessage: String, maxLen: Int = 40): String {
    val clean = firstMessage.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return "New chat"
    return if (clean.length <= maxLen) clean else clean.take(maxLen - 1).trimEnd() + "…"
}
