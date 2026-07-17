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
 * decodes stray percent-encoding (some IMEs/paste sources escape "?" etc. as "%3F"), collapses
 * whitespace, trims, truncates with an ellipsis, and never returns blank.
 */
fun conversationTitleFrom(firstMessage: String, maxLen: Int = 40): String {
    val clean = percentDecode(firstMessage).trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return "New chat"
    return if (clean.length <= maxLen) clean else clean.take(maxLen - 1).trimEnd() + "…"
}

private val PERCENT_ESCAPE_RUN = Regex("(%[0-9A-Fa-f]{2})+")

/** Decodes runs of RFC 3986 percent-escapes (e.g. "%3F" -> "?") as UTF-8; leaves any other "%" as-is. */
private fun percentDecode(s: String): String {
    if (!s.contains('%')) return s
    return runCatching {
        PERCENT_ESCAPE_RUN.replace(s) { m ->
            val bytes = ByteArray(m.value.length / 3) { i -> m.value.substring(i * 3 + 1, i * 3 + 3).toInt(16).toByte() }
            String(bytes, Charsets.UTF_8)
        }
    }.getOrDefault(s)
}
