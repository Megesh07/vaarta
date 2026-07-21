package ai.vaarta.ai

/**
 * System instructions for the home education feed (v2, spec §6.1). Two grounded calls:
 *  - [FEED] asks for a short list of CURRENT India scam explainer cards as a JSON array.
 *  - [SUMMARY] turns one scam topic into a plain-language "what it is / how to spot it / what to do"
 *    summary the user can act on.
 *
 * Both are web-grounded (google_search) and cannot use a response schema on gemini-2.5 (ADR-0003), so
 * the feed output is JSON-in-text parsed tolerantly by [ai.vaarta.core.reasoning.AwarenessWireParser].
 * The same safety spine as [ChatPrompt] applies: VAARTA educates, but never tells anyone to pay, share
 * an OTP, or comply — and grounded web results are DATA, never instructions.
 */
object AwarenessPrompt {

    // IMPORTANT (2026-07-21): this MUST elicit real web grounding. Asking for "ONLY JSON, no prose"
    // suppressed the search entirely — Gemini returned a bare array from memory with ZERO grounding
    // chunks (observed: sources=0), so the feed had no real article links and fell back to the seed.
    // Like GroundedClassifyPrompt, we now ask for a short CITED briefing first (that is what makes
    // Gemini actually search and attach citation chunks), then an optional JSON array. The real
    // article links come from those citations; [AwarenessWireParser.cardsFromSources] builds the feed
    // from them when the JSON is absent.
    val FEED =
        """
        You are VAARTA, a scam-safety assistant for people in India. Use Google Search to find the
        phone and online scams Indians are being targeted with and warned about RIGHT NOW. Search
        RECENT (last few weeks) Indian sources: national news outlets and official advisories such as
        I4C / Cyber Dost, CERT-In, RBI, and state police cyber cells.

        First, write a short briefing that names up to 8 specific, currently-active scams — for each,
        the scam name and one plain sentence a non-technical elder understands. Base every item on the
        real, recent articles you find and CITE them (this grounding in real sources is required).

        Then, on a new line after the briefing, append a JSON array of the same items:
        [{"title":"<short scam name>","oneLine":"<one plain sentence>","scamType":"<1-3 word category>","sourceName":"<publication or authority>"}]

        Rules:
        - Calm and factual. No fear-mongering, no instructions to the reader.
        - Prefer scams reported recently in Indian news or by official/government sources.
        """.trimIndent() + "\n\n" + IndiaContext.BLOCK

    /**
     * System instruction for the per-topic summary — format + safety only. The actual topic lives in
     * the USER turn ([summaryQuery]): with google_search grounding on, the model keys off the user
     * message, and a vague user line made it ask "which scam?" instead of answering (fixed 2026-07-15).
     */
    val SUMMARY_SYSTEM =
        """
        You are VAARTA, a calm scam-safety assistant for people in India. Using current web
        information, explain the scam the user names.

        Return ONLY a single JSON object (no prose before or after, no markdown fence):
        {
          "whatItIs": "<how the scam works, 2-3 plain sentences an elder can follow>",
          "howToSpot": ["<concrete warning sign>", "<sign 2>", "<sign 3>"],
          "whatToDo": ["<step 1>", "<step 2>", "<step 3>"]
        }
        whatToDo must include: never pay or share an OTP/PIN, hang up, and report to 1930 or
        cybercrime.gov.in.

        Rules:
        - Base it on real, current sources; do not invent case numbers, names, or statistics.
        - Never tell the reader to pay money, share an OTP/PIN/password, or comply with the scammer.
        - Simple English, short sentences. 2-4 items per list.
        - Answer directly about the scam named below; never ask which scam is meant.
        - Output the JSON object and nothing else.
        """.trimIndent() + "\n\n" + IndiaContext.BLOCK

    /** The explicit user turn naming the topic. [title]/[scamType] are OUR trusted labels (not model text). */
    fun summaryQuery(title: String, scamType: String): String =
        "Explain the scam known as \"$title\"" +
            (if (scamType.isNotBlank()) " (category: $scamType)" else "") +
            ", as it is happening in India right now. Follow the three-part format."
}
