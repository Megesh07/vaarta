package ai.vaarta.core.reasoning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One card in the home "Trending scams in India" education feed (v2, spec §6.1). Deliberately just an
 * explainer topic — [title], a one-line hook ([oneLine]), a short [scamType] tag, and a plain-text
 * [sourceName] label (e.g. "The Hindu"). It carries NO trusted URL: the authoritative, tappable,
 * grounding-cited links are produced fresh by [ArticleSummary.sources] when the user opens a card, so
 * we never surface a link the feed model merely typed (which could be fabricated).
 */
@Serializable
data class AwarenessCard(
    val title: String,
    val oneLine: String,
    val scamType: String = "",
    val sourceName: String = "",
)

/** A plain-language AI summary of a scam topic + the real web sources it was grounded on (§6.1). */
data class ArticleSummary(val text: String, val sources: List<Source> = emptyList())

/** Raw wire shape of one feed card from the grounded model's JSON-in-text (no strict schema on 2.5). */
@Serializable
private data class AwarenessCardWire(
    val title: String = "",
    val oneLine: String = "",
    val scamType: String = "",
    val sourceName: String = "",
)

/**
 * Pure parsing/validation for the home education feed — kept in core:reasoning so it is unit-testable
 * without Android or the network, exactly like [CoachingWireParser]. The grounded feed call can't use
 * a response schema on gemini-2.5 (ADR-0003), so output is JSON-in-text (an array, possibly wrapped in
 * prose or a markdown fence). This extracts the array and validates each card, and **fails closed** to
 * an empty list on anything malformed — the caller then falls back to the bundled seed so the screen
 * is never empty and never shows garbage.
 */
object AwarenessWireParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Max cards surfaced on the home feed — keeps it scannable and caps a runaway model response. */
    const val MAX_CARDS = 8

    /** Parses the grounded model's text into validated cards. Returns empty on any failure. */
    fun parseFeed(modelText: String?): List<AwarenessCard> {
        val arrayText = extractJsonArray(modelText) ?: return emptyList()
        val wires = runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AwarenessCardWire.serializer()), arrayText)
        }.getOrNull() ?: return emptyList()
        return wires
            .mapNotNull { w ->
                val title = w.title.trim()
                val oneLine = w.oneLine.trim()
                if (title.isEmpty() || oneLine.isEmpty()) return@mapNotNull null
                AwarenessCard(
                    title = title,
                    oneLine = oneLine,
                    scamType = w.scamType.trim(),
                    sourceName = w.sourceName.trim(),
                )
            }
            .distinctBy { it.title.lowercase() }
            .take(MAX_CARDS)
    }

    /**
     * Leniently pulls a JSON array-of-objects out of a text blob. Grounded output isn't strict JSON —
     * it may wrap the array in prose or a markdown fence AND sprinkle citation markers like `[1]`,
     * `[2]` in the surrounding text. So we don't just grab the first `[`: we find the first `[` whose
     * next non-space char is `{` (the real array start) and bracket-depth-scan to its matching `]`.
     * Returns null if no such array is present.
     */
    private fun extractJsonArray(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var i = 0
        while (i < text.length) {
            if (text[i] == '[' && nextNonSpaceIs(text, i + 1, '{')) {
                val end = matchingArrayEnd(text, i)
                if (end != -1) return text.substring(i, end + 1)
            }
            i++
        }
        return null
    }

    private fun nextNonSpaceIs(text: String, from: Int, target: Char): Boolean {
        var j = from
        while (j < text.length && text[j].isWhitespace()) j++
        return j < text.length && text[j] == target
    }

    /** Bracket-depth scan from an opening `[`, ignoring brackets inside JSON strings. */
    private fun matchingArrayEnd(text: String, open: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var i = open
        while (i < text.length) {
            val ch = text[i]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                !inString && ch == '[' -> depth++
                !inString && ch == ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }
}
