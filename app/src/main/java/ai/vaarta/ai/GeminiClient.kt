package ai.vaarta.ai

import ai.vaarta.BuildConfig
import ai.vaarta.core.reasoning.LiveSuggestion
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Text-mode bridge to Gemini `generateContent` — turns the latest caller line into ONE structured
 * [LiveSuggestion] (ADR-0002). This is the proven-in-REST path; the live-audio WebSocket layer is
 * added later. Every call is blocking (invoke from Dispatchers.IO) and **fails closed**: missing
 * key, network error, non-200, or unparseable output all return null, and the caller falls back to
 * the deterministic question bank.
 *
 * Safety is layered here (ADR-0002): the specialized system instruction constrains the model to the
 * scam-verification task; the caller line is embedded as JSON-escaped data (never as instructions);
 * output is schema-constrained; and the returned text MUST still pass SuggestionSafetyFilter before
 * display (the ViewModel does that — this client does not decide what is safe to show).
 */
object GeminiClient {

    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val TIMEOUT_MS = 8_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Specialized system instruction — shared with the live client so the two never drift. */
    private val SYSTEM_INSTRUCTION = SharedScamPrompt.INSTRUCTION +
        "\n\nOutput ONLY the JSON object requested."

    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** Returns a suggestion, or null on any failure (fails closed). Blocking — call off the main thread. */
    fun suggest(callerLine: String, stage: String): LiveSuggestion? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || callerLine.isBlank()) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildRequestBody(callerLine, stage)) ?: return null
            parseSuggestion(response)
        } catch (e: Exception) {
            // DIAGNOSTIC (temporary): type + message only — never the key, URL, or caller's words.
            Log.w("GeminiClient", "suggest failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildRequestBody(callerLine: String, stage: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", SYSTEM_INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject {
                        // callerLine is JSON-escaped by put(): it can never break out of the string
                        // or inject request structure. Semantic injection is handled by the system
                        // instruction + SuggestionSafetyFilter downstream.
                        put("text", "Call stage reached: $stage. The caller just said: \"$callerLine\". Suggest what the user should say back.")
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("suggestedReply") { put("type", "string") }
                    putJsonObject("why") { put("type", "string") }
                    putJsonObject("category") { put("type", "string") }
                    putJsonObject("confidence") { put("type", "number") }
                }
                putJsonArray("required") { add("suggestedReply"); add("why"); add("category"); add("confidence") }
            }
            put("temperature", 0.3)
            put("maxOutputTokens", 300)
            putJsonObject("thinkingConfig") { put("thinkingBudget", 0) } // OFF: latency + token budget (ADR-0002)
        }
    }.toString()

    private fun post(urlStr: String, body: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                // DIAGNOSTIC (temporary): log code + Google's error body (no key, no PII in it).
                val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                Log.w("GeminiClient", "HTTP $code: ${err?.take(300)}")
                null
            } else {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** The model's JSON is nested as a string inside candidates[0].content.parts[0].text. */
    private fun parseSuggestion(response: String): LiveSuggestion? {
        val inner = runCatching {
            json.parseToJsonElement(response)
                .jsonObject["candidates"]?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("text")?.jsonPrimitive?.content
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(LiveSuggestion.serializer(), inner) }.getOrNull()
    }
}
