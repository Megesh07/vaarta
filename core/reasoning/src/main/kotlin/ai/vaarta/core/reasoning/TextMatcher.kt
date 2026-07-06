package ai.vaarta.core.reasoning

import ai.vaarta.core.common.MatchMode
import ai.vaarta.core.common.Normalization
import ai.vaarta.core.common.Signal

/**
 * Matches signal patterns against a normalized transcript — designed for NOISY ASR output
 * (AI_REASONING_ENGINE.md §3, INDIAN_LANGUAGE_SUPPORT.md §4). Fuzzy matching tolerates ASR errors
 * ("digital arrest" ~ "digital a rest") by comparing on the whitespace-stripped text, so word
 * boundaries the ASR gets wrong do not defeat a match.
 */
object TextMatcher {

    /** True if any phrase for any active language matches [normalizedText]. */
    fun matches(normalizedText: String, signal: Signal, activeLanguages: List<String>): Boolean {
        for (lang in activeLanguages) {
            val phrases = signal.patterns[lang] ?: continue
            for (phrase in phrases) {
                val p = Normalization.normalize(phrase)
                if (p.isNotEmpty() && matchPhrase(normalizedText, p, signal.match)) return true
            }
        }
        return false
    }

    private fun matchPhrase(text: String, phrase: String, mode: MatchMode): Boolean {
        if (mode == MatchMode.REGEX) return runCatching { Regex(phrase).containsMatchIn(text) }.getOrDefault(false)
        val maxDist = when (mode) {
            MatchMode.FUZZY1 -> 1
            MatchMode.FUZZY2 -> 2
            else -> 0 // EXACT, STEM
        }
        val hay = text.replace(" ", "")
        val needle = phrase.replace(" ", "")
        if (needle.isEmpty()) return false
        if (maxDist == 0) return hay.contains(needle)

        val n = needle.length
        val maxStart = hay.length - (n - maxDist)
        var start = 0
        while (start <= maxStart) {
            var len = (n - maxDist).coerceAtLeast(1)
            val maxLen = n + maxDist
            while (len <= maxLen && start + len <= hay.length) {
                if (levenshtein(hay.substring(start, start + len), needle) <= maxDist) return true
                len++
            }
            start++
        }
        return false
    }

    /** Standard Levenshtein edit distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
