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

    val FEED =
        """
        You are VAARTA, a scam-safety assistant for people in India. Using current web information,
        list the scams that Indians are being targeted with RIGHT NOW (e.g. digital-arrest, fake
        police/CBI/ED/customs, courier/FedEx parcel, KYC/bank, UPI/refund, loan-app, investment/trading,
        task/part-time-job, lottery/KBC frauds).

        Return ONLY a JSON array (no prose, no markdown) of up to 8 objects, each:
        {
          "title": "<short scam name, max ~6 words>",
          "oneLine": "<one plain sentence a non-technical elder understands: what the scammer does>",
          "scamType": "<a 1-3 word category tag>",
          "sourceName": "<the publication or authority you saw it in, e.g. 'The Hindu', 'I4C'>"
        }

        Rules:
        - Keep every line simple, calm, and factual. No fear-mongering, no instructions to the reader.
        - Prefer scams reported recently in Indian news or by official sources.
        - Output the JSON array and nothing else.
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
