package ai.vaarta.core.reasoning

/**
 * The Open Graph / Twitter-card fields we lift from a real article page (live-news feed redesign
 * 2026-07-21): the page's own headline ([title]), its share-preview image ([imageUrl] — the
 * "eye-catching" photo that appears when the article is shared), and the publisher ([siteName]).
 *
 * These make a feed card show the REAL article — its real headline and real photo — rather than a
 * model-written caption over a generic illustration. Every field is best-effort and nullable; a page
 * that exposes none simply yields null and the card keeps its illustration/model caption.
 */
data class OgTags(
    val title: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
)

/**
 * Pure, network-free parser for a page's `<meta property="og:*">` / `<meta name="twitter:*">` tags.
 * Kept in core:reasoning so it is unit-testable exactly like [AwarenessWireParser]. Tolerant of
 * attribute order (`property` before or after `content`), single or double quotes, and the common
 * `twitter:` fallbacks. Only ABSOLUTE http(s) image URLs are accepted (a relative/`data:` image is
 * dropped rather than shown broken). Fails closed to null when nothing useful is present.
 */
object OgTagParser {

    private val META = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)

    /** Parses [html] into [OgTags], or null if no usable og/twitter fields are found. */
    fun parse(html: String?): OgTags? {
        if (html.isNullOrBlank()) return null
        val map = HashMap<String, String>()
        for (m in META.findAll(html)) {
            val tag = m.value
            val key = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
            if (!key.startsWith("og:") && !key.startsWith("twitter:")) continue
            val content = attr(tag, "content")?.let(HtmlEntities::decode) ?: continue
            map.putIfAbsent(key, content) // first occurrence wins
        }
        val image = firstNonBlank(map, "og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")
            ?.let(::absoluteHttpOrNull)
        val title = firstNonBlank(map, "og:title", "twitter:title")
        val site = firstNonBlank(map, "og:site_name")
        if (image == null && title == null && site == null) return null
        return OgTags(title = title, imageUrl = image, siteName = site)
    }

    private fun attr(tag: String, name: String): String? {
        val r = Regex("""\b${name}\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = r.find(tag) ?: return null
        val v = m.groupValues[2].ifEmpty { m.groupValues[3] }.trim()
        return v.ifEmpty { null }
    }

    private fun firstNonBlank(map: Map<String, String>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { map[it]?.trim()?.ifBlank { null } }

    /** Accepts only absolute http(s) URLs; anything relative/other-scheme → null (never a broken img). */
    private fun absoluteHttpOrNull(url: String): String? {
        val u = url.trim()
        return if (u.startsWith("http://", true) || u.startsWith("https://", true)) u else null
    }

}

/**
 * Minimal HTML-entity decoder for the entities that show up in real news og:title / og:image values —
 * named ones plus ANY numeric (`&#39;`, `&#039;`) or hex (`&#x27;`) reference. Kept general because
 * publishers use every spelling of an apostrophe/ampersand; a partial list left raw `&#039;` visible
 * in headlines (observed live 2026-07-21). Not a full spec decoder — just enough to render clean text.
 */
object HtmlEntities {
    private val NAMED = mapOf(
        "&amp;" to "&", "&quot;" to "\"", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ",
        "&rsquo;" to "’", "&lsquo;" to "‘", "&ldquo;" to "“", "&rdquo;" to "”",
        "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
    )
    private val NUMERIC = Regex("&#(x?)([0-9a-fA-F]+);")

    fun decode(s: String): String {
        var out = s
        for ((k, v) in NAMED) out = out.replace(k, v, ignoreCase = true)
        out = NUMERIC.replace(out) { m ->
            val code = runCatching {
                if (m.groupValues[1].isEmpty()) m.groupValues[2].toInt(10) else m.groupValues[2].toInt(16)
            }.getOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
        return out.trim()
    }
}
