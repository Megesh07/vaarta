package ai.vaarta.core.reasoning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One card in the home "Trending scams in India" education feed (spec §6.1; live-news redesign
 * 2026-07-21). A card is EITHER a real, current news article or a bundled category explainer:
 *  - [title] — the real article headline (upgraded to the page's own og:title when scraped) or the
 *    category name for a seed card.
 *  - [oneLine] — a short AI hook.
 *  - [scamType] — a 1-3 word category tag (drives the illustration + Related grouping).
 *  - [sourceName] — the publisher/domain (e.g. "The Hindu", "ndtv.com").
 *  - [url] — the REAL, tappable article link. **Non-null ONLY when it came from Gemini grounding
 *    metadata** ([Source.uri]); never a link the feed model merely typed (which could be fabricated).
 *    Null = a seed/offline card, which opens the AI-summary page instead of the real article.
 *  - [imageUrl] — the article's REAL preview image (its own og:image), or null → the card falls back
 *    to the category illustration. Never a model-typed or fabricated image.
 */
@Serializable
data class AwarenessCard(
    val title: String,
    val oneLine: String,
    val scamType: String = "",
    val sourceName: String = "",
    val url: String? = null,
    val imageUrl: String? = null,
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

    /**
     * Parses the grounded model's text into validated cards and attaches REAL article URLs from the
     * grounding [sources]. Returns empty on any parse failure. The URL on a card is taken ONLY from
     * [sources] (Gemini grounding metadata) — never from model text, so we can never surface a link
     * the model merely typed.
     */
    fun parseFeed(modelText: String?, sources: List<Source> = emptyList()): List<AwarenessCard> {
        val arrayText = extractJsonArray(modelText) ?: return emptyList()
        val wires = runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AwarenessCardWire.serializer()), arrayText)
        }.getOrNull() ?: return emptyList()
        val cards = wires
            .mapNotNull { w ->
                val title = w.title.trim()
                val oneLine = w.oneLine.trim()
                if (title.isEmpty() || oneLine.isEmpty()) return@mapNotNull null
                AwarenessCard(
                    title = HtmlEntities.decode(title),
                    oneLine = HtmlEntities.decode(oneLine),
                    scamType = w.scamType.trim(),
                    sourceName = w.sourceName.trim(),
                )
            }
            .distinctBy { it.title.lowercase() }
            .take(MAX_CARDS)
        return attachRealUrls(cards, sources)
    }

    /**
     * Builds real-article cards straight from grounding [sources] — the fallback when the grounded
     * model won't emit a usable JSON array (common with google_search on, where output is grounded
     * prose + citations rather than clean JSON). Each card is a REAL link; the og:title/og:image
     * scrape then fills in the real headline and photo, so a domain-titled card becomes a proper
     * article card. This is what keeps the feed genuinely live even when the JSON path fails.
     */
    fun cardsFromSources(sources: List<Source>): List<AwarenessCard> =
        sources.asSequence()
            // Keep only sources whose title is a real HEADLINE — grounding also cites bare homepages
            // ("youtube.com", "axis.bank.in") whose title is just a domain; those make ugly, useless
            // cards, so we drop them rather than surface them (if none qualify, the feed keeps its
            // cache/seed instead of showing junk).
            .filter { it.uri.isNotBlank() && looksLikeHeadline(it.title) }
            .distinctBy { it.uri }
            .map { s ->
                AwarenessCard(
                    title = HtmlEntities.decode(s.title.ifBlank { hostLabel(s.uri) ?: "Scam news" }),
                    oneLine = "",
                    scamType = "",
                    sourceName = hostLabel(s.uri) ?: HtmlEntities.decode(s.title),
                    url = s.uri,
                )
            }
            .take(MAX_CARDS)
            .toList()

    /**
     * Attaches REAL, grounding-cited article URLs to feed cards. A URL is taken ONLY from [sources]
     * (Gemini grounding metadata), never from model text — an unbacked card simply keeps `url = null`
     * and opens the AI-summary page. Matching is topical/publisher first (keeps scamType coherent),
     * then a positional fill for leftover cards: every grounded source for a "current scams" query is
     * itself a scam article, and the og:title/og:image scrape later re-captions each card from its
     * real linked page, so even a positionally-paired card ends up showing that article's own
     * headline and photo.
     */
    internal fun attachRealUrls(cards: List<AwarenessCard>, sources: List<Source>): List<AwarenessCard> {
        if (sources.isEmpty() || cards.isEmpty()) return cards
        val used = BooleanArray(sources.size)
        val result = arrayOfNulls<AwarenessCard>(cards.size)
        // Pass 1: topical / publisher match (keeps the category tag coherent with the link).
        for ((ci, card) in cards.withIndex()) {
            val idx = bestSourceIndex(card, sources, used)
            if (idx != null) { used[idx] = true; result[ci] = withSource(card, sources[idx]) }
        }
        // Pass 2: positional fill so cards still get a real link when text matching couldn't align.
        var s = 0
        for (ci in cards.indices) {
            if (result[ci] != null) continue
            while (s < sources.size && used[s]) s++
            result[ci] = if (s < sources.size) { used[s] = true; withSource(cards[ci], sources[s]) } else cards[ci]
        }
        return result.map { it!! }
    }

    private fun withSource(card: AwarenessCard, source: Source): AwarenessCard =
        card.copy(url = source.uri, sourceName = card.sourceName.ifBlank { hostLabel(source.uri) ?: source.title })

    private fun bestSourceIndex(card: AwarenessCard, sources: List<Source>, used: BooleanArray): Int? {
        val cardTokens = tokens(card.sourceName) + tokens(card.title)
        if (cardTokens.isEmpty()) return null
        var best = -1
        var bestScore = 0
        for (i in sources.indices) {
            if (used[i]) continue
            val s = sources[i]
            val srcTokens = tokens(s.title) + tokens(hostLabel(s.uri) ?: "")
            val score = cardTokens.count { it in srcTokens }
            if (score > bestScore) { bestScore = score; best = i }
        }
        return if (bestScore >= 1) best else null
    }

    // Generic scam/English filler dropped so matching keys on the discriminating words (the scam
    // family + publisher), not on "scam"/"fraud"/"india" that every card and source shares.
    private val STOP = setOf(
        "scam", "scams", "fraud", "frauds", "the", "and", "for", "new", "alert", "alerts", "warning",
        "india", "indian", "indians", "people", "money", "case", "cases", "call", "calls", "phone",
        "online", "cyber", "how", "what", "your", "with", "from", "this", "that", "are", "you",
    )

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }.toSet()

    /** True when a grounding chunk's title reads like an article headline rather than a bare domain
     *  ("youtube.com", "axis.bank.in"): multi-word, of reasonable length, and not a lone hostname. */
    private fun looksLikeHeadline(title: String): Boolean {
        val t = title.trim()
        if (t.length < 12 || !t.contains(' ')) return false
        // A hostname like "some.thing.in Latest" is unusual; a real headline has ≥3 words.
        return t.split(Regex("\\s+")).size >= 3
    }

    /** Best-effort host label from a URL (e.g. "https://www.ndtv.com/x" -> "ndtv.com"). Grounding
     *  URIs are Google redirect links with no publisher host — those return null. */
    private fun hostLabel(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = Regex("^[a-z]+://([^/]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase()
            ?: return null
        val h = host.removePrefix("www.")
        if (h.contains("google") || h.contains("gstatic") || h.contains("vertexaisearch")) return null
        return h.ifBlank { null }
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
