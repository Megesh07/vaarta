package ai.vaarta.ai

/**
 * VAARTA supports only English, Hindi (Devanagari), and Hinglish (Latin-script Hindi) — P1/P2
 * regional languages are explicitly out of scope (ADR-0001). A coalesced live-transcription turn
 * containing no Latin or Devanagari letters at all cannot be genuine speech in a supported
 * language, so it is treated as an ASR hallucination rather than real caller speech (see
 * PROJECT_STATUS.md `task_b91d4a05`: Gemini Live's `inputTranscription` was observed fabricating
 * fluent Tamil/Telugu sentences from near-silence on a real-device test).
 *
 * IMPLEMENTATION_GUARDRAILS.md #9: transcript-derived text is untrusted input at every LLM
 * boundary — this is that check for the live-audio path, applied before the turn ever reaches
 * the risk engine, chat history, or the coach prompt.
 */
object TranscriptPlausibility {
    fun isPlausible(text: String): Boolean = text.any { isLatinLetter(it) || isDevanagari(it) }

    private fun isLatinLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
    private fun isDevanagari(c: Char): Boolean = c.code in 0x0900..0x097F
}
