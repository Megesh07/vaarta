package ai.vaarta.core.common

import java.text.Normalizer

/**
 * Text normalization for pattern matching — INDIAN_LANGUAGE_SUPPORT.md §3.
 * All matching happens on normalized text. Deliberately minimal and fully documented.
 *
 * Scope (MVP, ADR-0001): the ISO-15919 romanization channel (§3.4), which lets one romanized
 * pattern match native-script speech, is out of MVP scope. For now packs carry per-script
 * variants and matching is same-script + fuzzy. This function fully performs NFC + digit folding
 * + case + whitespace normalization; it is not a placeholder.
 */
object Normalization {

    private val whitespace = Regex("\\s+")

    /** Anything that is not a letter, number, or combining mark (Indic matras!) becomes a space. */
    private val nonToken = Regex("[^\\p{L}\\p{N}\\p{M}]+")

    /**
     * NFC-normalize, fold Unicode decimal digits to ASCII, drop punctuation/symbols (while keeping
     * Indic combining marks), lowercase (Latin), and collapse whitespace.
     */
    fun normalize(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) sb.append(asciiDigit(ch) ?: ch)
        val cleaned = nonToken.replace(sb.toString(), " ")
        return whitespace.replace(cleaned.lowercase().trim(), " ")
    }

    /** Devanagari/Tamil/Bengali/Arabic-Indic decimal digit -> ASCII; null if already ASCII or non-digit. */
    private fun asciiDigit(ch: Char): Char? {
        if (ch in '0'..'9') return null
        val d = Character.digit(ch, 10)
        return if (d in 0..9) ('0' + d) else null
    }
}
