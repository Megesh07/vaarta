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
data class ArticleSummary(
    val text: String,
    val sources: List<Source> = emptyList(),
    /** Present when the model's reply parsed into designed sections (redesign spec §7); null = prose fallback. */
    val structured: StructuredSummary? = null,
)

/** The designed three-part article shape (redesign spec §7): prose + checklist + numbered steps. */
data class StructuredSummary(
    val whatItIs: String,
    val howToSpot: List<String>,
    val whatToDo: List<String>,
)

/** Raw wire shape of the structured summary from the grounded model's JSON-in-text. */
@Serializable
private data class StructuredSummaryWire(
    val whatItIs: String = "",
    val howToSpot: List<String> = emptyList(),
    val whatToDo: List<String> = emptyList(),
)

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
    // Lenient on purpose: grounded model JSON drifts (trailing commas, relaxed quoting). Every
    // decode is still fail-closed — leniency widens what parses, never what gets shown.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

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
     * Parses the structured article summary (redesign spec §7) from JSON-in-text. Tolerates
     * preamble prose, markdown fences, and citation markers exactly like [parseFeed]; fails
     * closed to null (caller falls back to prose, then the card's one-liner). Requires a
     * non-blank whatItIs and at least one spot sign and one step — a partial object is worse
     * than the prose fallback.
     */
    fun parseStructuredSummary(modelText: String?): StructuredSummary? {
        val objectText = extractJsonObject(modelText) ?: return null
        val wire = runCatching {
            json.decodeFromString(StructuredSummaryWire.serializer(), objectText)
        }.getOrNull() ?: return null
        val whatItIs = wire.whatItIs.trim()
        val spot = wire.howToSpot.map { it.trim() }.filter { it.isNotEmpty() }
        val steps = wire.whatToDo.map { it.trim() }.filter { it.isNotEmpty() }
        if (whatItIs.isEmpty() || spot.isEmpty() || steps.isEmpty()) return null
        return StructuredSummary(whatItIs, spot, steps)
    }

    /** Leniently pulls the first JSON object out of a text blob (brace-depth scan, string-aware). */
    private fun extractJsonObject(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val open = text.indexOf('{')
        if (open == -1) return null
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
                !inString && ch == '{' -> depth++
                !inString && ch == '}' -> { depth--; if (depth == 0) return text.substring(open, i + 1) }
            }
            i++
        }
        return null
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
