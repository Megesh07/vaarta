package ai.vaarta.ai

/**
 * Backoff policy for transient Gemini API failures — 429 (free-tier quota) and 503 (model
 * overloaded) both routinely clear within a couple of seconds, so a couple of short retries
 * recover a call that would otherwise fall back to the safety message on the very first hiccup.
 */
object GeminiRetry {
    const val MAX_ATTEMPTS = 3 // 1 initial try + 2 retries
    val RETRYABLE_HTTP_CODES = setOf(429, 503)

    /** Delay before the next attempt. Honors Google's `Retry-After` (seconds) when present, capped
     *  at 10s so a large value can't stall the caller; otherwise a short fixed backoff by attempt
     *  number (1s, 2s, ...). */
    fun delayMs(retryAfterHeader: String?, attempt: Int): Long =
        (retryAfterHeader?.toLongOrNull()?.times(1000) ?: (1_000L * attempt)).coerceIn(0, 10_000)
}
