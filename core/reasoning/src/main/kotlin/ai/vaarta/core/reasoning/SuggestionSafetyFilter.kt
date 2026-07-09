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
 * phrasings that are specifically dangerous in a live scam: paying/transferring, disclosing an
 * OTP/PIN/Aadhaar/bank detail, complying with isolation ("don't tell your family" / staying on the
 * line), and installing remote-access software (ADR-0003, English + Hindi/Hinglish).
 *
 * A reply is the USER's own line back to the caller, so polarity matters: "I will NOT transfer the
 * money" must stay ACCEPTED (it's the safe refusal), while "I will transfer the money" must be
 * REJECTED. The payment/disclosure/stay-on-line/remote-access checks below therefore only fire on an
 * AFFIRMATIVE compliance framing (an explicit lead-in like "I will"/"sure"/"just" immediately
 * followed by the danger verb) — inserting "not" between the lead-in and the verb breaks the match,
 * which is what keeps refusals accepted without a separate negation list. Isolation is the one
 * inverted case: NOT telling family is the danger, so that phrase is banned outright regardless of
 * lead-in (see `isolationCompliance` below).
 */
object SuggestionSafetyFilter {

    sealed interface Result {
        object Accepted : Result
        data class Rejected(val reason: String) : Result
    }

    // Phrases that are unsafe unconditionally — no polarity ambiguity, always rejected.
    private val bannedPatterns: List<Regex> = listOf(
        // Legal advice (AI_REASONING_ENGINE.md §5, verbatim spec) — never weaken this one (S3).
        Regex("""you\s+should\s+(not\s+)?(pay|refuse|sue|comply)""", RegexOption.IGNORE_CASE),
        Regex("""(this|that|it'?s)\s+is\s+(il)?legal""", RegexOption.IGNORE_CASE),
        Regex("""you\s+have\s+the\s+right\s+to""", RegexOption.IGNORE_CASE),
        // Advising the victim to pay/transfer/comply — the single most dangerous possible output.
        Regex("""\b(just|please|go\s+ahead\s+and|you\s+can|you\s+could|better\s+to)\s+(pay|transfer|send\s+the\s+money|deposit)""", RegexOption.IGNORE_CASE),
        Regex("""it'?s\s+(safe|okay|ok|fine|best)\s+to\s+(pay|transfer|comply|cooperate)""", RegexOption.IGNORE_CASE),
        Regex("""make\s+the\s+payment""", RegexOption.IGNORE_CASE),
        Regex("""pay\s+the\s+(fine|fee|amount|money|deposit|penalty)""", RegexOption.IGNORE_CASE),
        // Generic "just comply / do what they say" — as dangerous as naming the specific demand.
        Regex("""\bcooperate\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdo\s+what\s+(they|he|she|the\s+officer)\s+says?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfollow\s+(their|his|her)\s+instructions\b""", RegexOption.IGNORE_CASE),
        // Isolation compliance — negating "tell" IS the danger (inverted polarity vs. payment/OTP).
        Regex("""\b(won'?t|will\s+not|don'?t|do\s+not|never)\s+tell\s+(my\s+)?(family|anyone|husband|wife|son|daughter|parents|police)\b""", RegexOption.IGNORE_CASE),
        Regex("""kisi\s+ko\s+(mat\s+batao|nahi[n]?\s+bataunga)""", RegexOption.IGNORE_CASE),
        // Accusing the specific caller (SCAM_INTELLIGENCE.md §9 — only "matches known patterns", never accuse).
        Regex("""(he|she|they|this\s+(person|caller))\s+(is|are)\s+(a\s+)?(criminal|fraudster|scammer|fraud|scam)""", RegexOption.IGNORE_CASE),
    )

    // Affirmative lead-ins: the danger verb must be IMMEDIATELY adjacent to one of these (only
    // whitespace between). "I will not transfer" fails to match because "not" breaks the adjacency —
    // this is the mechanism that keeps refusals accepted without a separate negation scan.
    private const val AFFIRMATIVE = """(?:i\s*(?:'ll|will|can|am\s+going\s+to)|we\s*(?:'ll|will|can)|just|please|go\s+ahead\s+and|you\s+can|you\s+could|better\s+to|sure,?|okay,?|ok,?|yes,?)"""

    private fun compliance(verbs: String, nouns: String): Regex =
        Regex("""\b$AFFIRMATIVE\s+(?:$verbs)\b[\s\S]{0,25}?\b(?:$nouns)\b""", RegexOption.IGNORE_CASE)

    // Payment/transfer compliance — extends the unconditional patterns above with a broader verb set.
    private val paymentCompliance =
        compliance("""pay|transfer|send|deposit|wire|scan""", """money|amount|fine|fee|penalty|deposit|funds|payment|upi|qr\s*code|account""")
    private val paymentComplianceHindi = Regex("""paisa\s*(transfer|bhej|bhejo|bhejenge|de\s*doonga|de\s*denge)""", RegexOption.IGNORE_CASE)

    // OTP/PIN/CVV/Aadhaar/PAN/bank-detail disclosure — the single most damaging real-world gap.
    private val disclosureCompliance = compliance(
        """share|give|tell|send|provide|reveal""",
        """otp|pin|cvv|aadhaar|adhaar|aadhar|pan(?:\s*(?:number|card))?|bank\s*details?|account\s*number|card\s*number|password""",
    )
    private val disclosureComplianceHindi = Regex("""(otp|pin|aadhaar|adhaar)\s*(bata|batao|share|de\s*doonga|de\s*denge)""", RegexOption.IGNORE_CASE)

    // Staying on the line / keeping this secret — the other half of isolation compliance.
    private val stayOnLineCompliance =
        compliance("""stay|remain|keep|continue""", """on\s+the\s+(?:line|call)|quiet|silent|secret""")

    // Remote-access / app-install / screen-share — the channel-switch step of the scam script.
    private val remoteAccessCompliance =
        compliance("""install|open|download|share""", """(?:the\s+)?(?:app|screen|anydesk|teamviewer|remote\s*access)""")

    /** Accepts a suggestion only if it is non-blank, matches no unconditional ban, and shows no
     *  affirmative compliance with payment/disclosure/isolation/remote-access demands. */
    fun check(text: String): Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.Rejected("empty suggestion")
        for (p in bannedPatterns) {
            if (p.containsMatchIn(trimmed)) return Result.Rejected("banned phrasing: ${p.pattern}")
        }
        if (paymentCompliance.containsMatchIn(trimmed) || paymentComplianceHindi.containsMatchIn(trimmed)) {
            return Result.Rejected("advises paying/transferring")
        }
        if (disclosureCompliance.containsMatchIn(trimmed) || disclosureComplianceHindi.containsMatchIn(trimmed)) {
            return Result.Rejected("advises sharing OTP/PIN/ID/bank details")
        }
        if (stayOnLineCompliance.containsMatchIn(trimmed)) {
            return Result.Rejected("advises staying on the line / keeping secret")
        }
        if (remoteAccessCompliance.containsMatchIn(trimmed)) {
            return Result.Rejected("advises installing app / sharing screen")
        }
        return Result.Accepted
    }

    /** Convenience: the suggestion text if safe, else null (caller falls back to the question bank).
     *  Also gates on [LiveSuggestion.why] — it is never shown today, but must never silently become
     *  an unfiltered surface if a future UI decides to render it. */
    fun sanitizedOrNull(suggestion: LiveSuggestion): String? {
        if (suggestion.why.isNotBlank() && check(suggestion.why) !is Result.Accepted) return null
        return (check(suggestion.suggestedReply) as? Result.Accepted)?.let { suggestion.suggestedReply }
    }

    /** Keeps only replies whose text passes [check] — the per-reply rail for the copilot (ADR-0003). */
    fun filterReplies(replies: List<Reply>): List<Reply> =
        replies.filter { check(it.text) is Result.Accepted }

    /**
     * Full-turn gate for the copilot: filters the warning and every reply, and fails the WHOLE turn
     * closed (null → caller falls back to the deterministic question) if the warning itself is
     * unsafe or no reply survives. Mirrors ADR-0002's "discard on schema/safety failure" rail.
     */
    fun sanitize(response: CoachingResponse): CoachingResponse? {
        if (check(response.warning) !is Result.Accepted) return null
        val safeReplies = filterReplies(response.replies)
        if (safeReplies.isEmpty()) return null
        return CoachingResponse(response.warning, safeReplies)
    }
}
