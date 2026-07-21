package ai.vaarta.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression cases for the "AI assistant isn't replying" report: a tester hit 429 (free-tier quota)
 * and 503 (model overloaded) from Gemini, both of which GeminiClient.post() gave up on immediately
 * instead of retrying, surfacing the safety fallback on the first transient hiccup.
 */
class GeminiRetryTest {

    @Test
    fun `429 and 503 are retryable`() {
        assertTrue(429 in GeminiRetry.RETRYABLE_HTTP_CODES)
        assertTrue(503 in GeminiRetry.RETRYABLE_HTTP_CODES)
    }

    @Test
    fun `client and other server errors are not retryable`() {
        assertFalse(400 in GeminiRetry.RETRYABLE_HTTP_CODES)
        assertFalse(401 in GeminiRetry.RETRYABLE_HTTP_CODES)
        assertFalse(500 in GeminiRetry.RETRYABLE_HTTP_CODES)
    }

    @Test
    fun `first retry backs off 1s without a Retry-After header`() {
        assertEquals(1_000L, GeminiRetry.delayMs(null, attempt = 1))
    }

    @Test
    fun `second retry backs off 2s without a Retry-After header`() {
        assertEquals(2_000L, GeminiRetry.delayMs(null, attempt = 2))
    }

    @Test
    fun `honors a Retry-After header given in seconds`() {
        assertEquals(5_000L, GeminiRetry.delayMs("5", attempt = 1))
    }

    @Test
    fun `caps an oversized Retry-After header at 10s`() {
        assertEquals(10_000L, GeminiRetry.delayMs("120", attempt = 1))
    }

    @Test
    fun `ignores a malformed Retry-After header and falls back to attempt backoff`() {
        assertEquals(1_000L, GeminiRetry.delayMs("not-a-number", attempt = 1))
    }
}
