package ai.vaarta.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins classify()'s generationConfig against a real regression (2026-07-24): with thinking left at
 * its default, the model's internal "thinking" tokens can consume the whole maxOutputTokens budget
 * before ever reaching the mandatory trailing JSON block, so classify() silently returns null for
 * that call. Every other GeminiClient call site already disables thinking (ADR-0002) — this pins
 * classify() to the same contract.
 */
class GeminiClassifyRequestBodyTest {
    @Test
    fun `classify request disables thinking so the JSON footer is never crowded out`() {
        val body = GeminiClient.buildClassifyRequestBody("Caller: this is a test.")
        val config = Json.parseToJsonElement(body).jsonObject["generationConfig"]!!.jsonObject

        assertEquals(1024, config["maxOutputTokens"]!!.jsonPrimitive.int)
        assertEquals(0, config["thinkingConfig"]!!.jsonObject["thinkingBudget"]!!.jsonPrimitive.int)
    }
}
