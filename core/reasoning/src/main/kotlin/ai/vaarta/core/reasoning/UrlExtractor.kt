package ai.vaarta.core.reasoning

/** Pure, Android-free URL extraction for scam-link checking (spec §13). */
object UrlExtractor {
    // http(s):// links, or bare domain+path like "sbi-verify.co/kyc". Deliberately conservative:
    // requires a dotted host, and (for bare hosts) a following path so plain "call me at 5.30" is
    // not treated as a domain.
    private val WITH_SCHEME = Regex("""https?://[^\s]+""")
    private val BARE_WITH_PATH = Regex("""\b(?:[a-z0-9-]+\.)+[a-z]{2,}/[^\s]*""", RegexOption.IGNORE_CASE)

    fun extract(text: String): List<String> {
        val out = LinkedHashSet<String>()
        WITH_SCHEME.findAll(text).forEach { out.add(it.value.trimEnd('.', ',', ')')) }
        BARE_WITH_PATH.findAll(text).forEach { m ->
            if (out.none { it.contains(m.value) }) out.add(m.value.trimEnd('.', ',', ')'))
        }
        return out.toList()
    }
}
