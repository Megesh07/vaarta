package ai.vaarta.core.reasoning

/**
 * The last line of defense before an AI-generated suggestion reaches a frightened user
 * (ADR-0002 rail #2). Runs at DISPLAY time on every suggestion — a scam-crafted or hallucinated
 * output that slips past the model's system instruction is caught here and discarded, falling back
 * to the deterministic question bank.
 *
 * Deliberately a DENY list of clearly-dangerous phrasings (defense in depth — the schema, the
 * system instruction, and this filter each catch different failures). It must NOT be so broad that
 * it rejects legitimate verification questions that happen to mention money ("why would an agency
 * need me to pay?") — those are exactly what we want the user to ask. Tests pin both directions.
 *
 * Patterns extend AI_REASONING_ENGINE.md §5's banned legal-advice phrasings, plus advise-to-comply
 * phrasings that are specifically dangerous in a live scam (telling the victim to pay/transfer).
 */
object SuggestionSafetyFilter {

    sealed interface Result {
        object Accepted : Result
        data class Rejected(val reason: String) : Result
    }

    private val bannedPatterns: List<Regex> = listOf(
        // Legal advice (AI_REASONING_ENGINE.md §5) — VAARTA gives verification help, never legal advice.
        Regex("""you\s+should\s+(not\s+)?(pay|refuse|sue|comply)""", RegexOption.IGNORE_CASE),
        Regex("""(this|that|it'?s)\s+is\s+(il)?legal""", RegexOption.IGNORE_CASE),
        Regex("""you\s+have\s+the\s+right\s+to""", RegexOption.IGNORE_CASE),
        // Advising the victim to pay/transfer/comply — the single most dangerous possible output.
        Regex("""\b(just|please|go\s+ahead\s+and|you\s+can|you\s+could|better\s+to)\s+(pay|transfer|send\s+the\s+money|deposit)""", RegexOption.IGNORE_CASE),
        Regex("""it'?s\s+(safe|okay|ok|fine|best)\s+to\s+(pay|transfer|comply|cooperate)""", RegexOption.IGNORE_CASE),
        Regex("""make\s+the\s+payment""", RegexOption.IGNORE_CASE),
        Regex("""pay\s+the\s+(fine|fee|amount|money|deposit|penalty)""", RegexOption.IGNORE_CASE),
        // Accusing the specific caller (SCAM_INTELLIGENCE.md §9 — only "matches known patterns", never accuse).
        Regex("""(he|she|they|this\s+(person|caller))\s+(is|are)\s+(a\s+)?(criminal|fraudster|scammer)""", RegexOption.IGNORE_CASE),
    )

    /** Accepts a suggestion only if it is non-blank and matches no banned pattern. */
    fun check(text: String): Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.Rejected("empty suggestion")
        for (p in bannedPatterns) {
            if (p.containsMatchIn(trimmed)) return Result.Rejected("banned phrasing: ${p.pattern}")
        }
        return Result.Accepted
    }

    /** Convenience: the suggestion text if safe, else null (caller falls back to the question bank). */
    fun sanitizedOrNull(suggestion: LiveSuggestion): String? =
        (check(suggestion.suggestedReply) as? Result.Accepted)?.let { suggestion.suggestedReply }
}
