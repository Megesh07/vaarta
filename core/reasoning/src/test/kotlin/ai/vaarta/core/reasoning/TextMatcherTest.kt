package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the fuzzy matcher against short-token false positives. "fir" (3 chars, FUZZY1) used to
 * substring-match "for"/"from"/"Sir" via edit-distance 1 on whitespace-stripped text, firing
 * SIG_LEGAL_THREAT on benign English speech.
 */
class TextMatcherTest {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val legalThreat = pack.signals.first { it.id == "SIG_LEGAL_THREAT" }
    private val langs = listOf("en", "hi_latn", "hi")

    private fun matches(text: String): Boolean =
        TextMatcher.matches(ai.vaarta.core.common.Normalization.normalize(text), legalThreat, langs)

    @Test
    fun `benign english words do not fire the legal-threat signal`() {
        assertFalse(matches("this is for you"), "'for' must not fuzzy-match")
        assertFalse(matches("i am calling from the bank"), "'from' must not fuzzy-match")
        assertFalse(matches("yes sir how can i help"), "'Sir' must not fuzzy-match")
    }

    @Test
    fun `genuine legal-threat phrases still fire`() {
        // NOTE: TextMatcher's fuzzy match is a whitespace-stripped CONTIGUOUS substring check
        // (see TextMatcher.kt matchPhrase) — the matched words must be adjacent in the transcript,
        // not merely present in the same sentence. "your fir registered" keeps "fir" and
        // "registered" adjacent so it matches the fixed "fir registered" pattern; a sentence like
        // "an fir has been registered" would NOT match (words separated) and must not be used here.
        assertTrue(matches("your fir registered against you"))
        assertTrue(matches("you are under digital arrest"))
    }
}
