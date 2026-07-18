package ai.vaarta.ai

import ai.vaarta.i18n.AppLanguage

/**
 * The two LLM language policies (redesign spec §3B.2) — kept in one place so both call sites read
 * the same contract instead of drifting:
 *  - [followUiLanguage] for GENERATED content with no user text to mirror (feed cards, article
 *    summaries) — states the target language outright.
 *  - [mirrorUserLanguage] for CONVERSATIONAL surfaces (chat) — mirrors the user's own latest
 *    language/script, with an explicit fallback for ambiguous input.
 * Both stay the LAST element appended to their prompt (2026-07-15 recency lesson: grounding/context
 * in a different language was biasing replies away from the intended one).
 */
object LanguageDirectives {

    private fun describe(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> "English"
        AppLanguage.HINDI -> "Hindi, written in Devanagari script (हिन्दी)"
        AppLanguage.HINGLISH -> "Hinglish — casual romanized Hindi/English code-mix, Latin letters only, never Devanagari"
    }

    fun followUiLanguage(language: AppLanguage): String =
        "MOST IMPORTANT: Write your ENTIRE response in ${describe(language)}. There is no user " +
            "message to mirror here — this is generated content, so state the language outright " +
            "rather than detecting it. Keep 1930, cybercrime.gov.in, Sanchar Saathi, UPI, OTP, and " +
            "bank/authority names untranslated in every language."

    /**
     * [uiDefault] is the fallback only for ambiguous latest-turn input (attachment-only, emoji-only,
     * a bare "hi") — the mirror rule otherwise always wins over the UI language (spec §3B.2 edge
     * case: "latest message wins").
     */
    fun mirrorUserLanguage(uiDefault: AppLanguage): String =
        "MOST IMPORTANT: Write your ENTIRE reply in the SAME language AND SCRIPT as the user's " +
            "LATEST message — even if the context, the article, or your web search results are in a " +
            "different language. Rules that matter here: " +
            "(1) Script preservation: if the user wrote Hindi or Tamil in Latin/romanized letters " +
            "(Hinglish/Tanglish), reply in Latin letters too — never switch to Devanagari or Tamil " +
            "script, even if it feels more 'correct'. " +
            "(2) Code-mixed input is valid as-is (e.g. \"Bhaiya this call bola parcel seized hai\") — " +
            "answer in the same mixed style; never refuse to answer or lecture the user about which " +
            "language to use. " +
            "(3) If the user switches language mid-conversation, follow the switch — the latest " +
            "message always wins over anything earlier. " +
            "(4) If the latest message is ambiguous (an attachment with no text, emoji-only, or a " +
            "bare greeting like \"hi\"/\"hello\"), answer in ${describe(uiDefault)} instead."
}
