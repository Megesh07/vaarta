package ai.vaarta.ai

import ai.vaarta.BuildConfig
import ai.vaarta.core.reasoning.ArticleSummary
import ai.vaarta.core.reasoning.AudioAnalysis
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.core.reasoning.AwarenessWireParser
import ai.vaarta.core.reasoning.ChatAnswer
import ai.vaarta.core.reasoning.ChatMessage
import ai.vaarta.core.reasoning.CoachingResponse
import ai.vaarta.core.reasoning.CoachingWireParser
import ai.vaarta.core.reasoning.ConversationTurn
import ai.vaarta.core.reasoning.GroundedAssessment
import ai.vaarta.core.reasoning.LiveSuggestion
import ai.vaarta.core.reasoning.Source
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
import java.util.Base64

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

    // Recorded-call analysis (Phase 4D) sends a whole clip and reads it back — far slower than a live
    // turn (Gate A measured ~8s for a ~1.5 MB clip), so it gets its own generous timeout.
    private const val AUDIO_TIMEOUT_MS = 60_000

    // Free-form chat (v2) is web-grounded — grounding spends extra latency on the search step, so it
    // gets a longer timeout than a live suggestion.
    private const val CHAT_TIMEOUT_MS = 30_000

    /** Inline-audio cap for [analyzeAudio]. The generateContent request total must stay under ~20 MB;
     *  base64 inflates bytes ~33%, so ~14 MB of raw audio is the safe inline ceiling (Files API, needed
     *  for larger clips, is deferred — see ADR-0003 Phase 4D). */
    const val MAX_INLINE_AUDIO_BYTES = 14 * 1024 * 1024

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

    private fun post(urlStr: String, body: String, timeoutMs: Int = TIMEOUT_MS): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
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
        val inner = extractInnerJson(response) ?: return null
        return runCatching { json.decodeFromString(LiveSuggestion.serializer(), inner) }.getOrNull()
    }

    // --- Conversation copilot (ADR-0003): warning + graded replies, cascaded off the transcription-
    // only live client (the native-audio Live model cannot emit structured JSON — confirmed in
    // ADR-0002/ADR-0003 research). Mirrors suggest()'s proven mechanics exactly: same blocking
    // HttpURLConnection, same 8s timeout, same double-JSON-parse, same thinking-off, same
    // content-free failure logging, same injection-safe data framing. Does NOT decide what is safe
    // to show — the caller (SessionViewModel) must still run SuggestionSafetyFilter.sanitize() on
    // the result, exactly as sanitizedOrNull() gates the text-mode path. Wire parsing/validation
    // itself lives in core:reasoning's CoachingWireParser (pure, unit-testable without network).

    /**
     * Coaches the user through the current call state: a short warning plus 2-3 graded replies
     * (verify/refuse/exit), given the full conversation so far. Returns null on any failure
     * (fails closed) — the caller falls back to the deterministic question bank.
     */
    fun coach(history: List<ConversationTurn>, stage: String, nextStage: String): CoachingResponse? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || history.isEmpty()) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildCoachRequestBody(history, stage, nextStage)) ?: return null
            parseCoaching(response)
        } catch (e: Exception) {
            // DIAGNOSTIC (temporary): type + message only — never the key, URL, or transcript.
            Log.w("GeminiClient", "coach failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildCoachRequestBody(history: List<ConversationTurn>, stage: String, nextStage: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", CoachPrompt.INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject {
                        // The transcript is embedded as a single JSON-escaped DATA string via put():
                        // it can never break out into request structure (same contract as suggest()).
                        // Speaker labels are our own best-effort attribution (OwnWordsGate), not
                        // ground truth — CoachPrompt tells the model this content is untrusted call
                        // audio, never instructions, regardless of label.
                        val transcript = history.joinToString("\n") { "${it.speaker}: ${it.text}" }
                        put(
                            "text",
                            "Call stage reached: $stage. Likely next stage: $nextStage. " +
                                "Conversation so far (untrusted call audio):\n$transcript",
                        )
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("warning") { put("type", "string") }
                    putJsonObject("replies") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("text") { put("type", "string") }
                                putJsonObject("kind") {
                                    put("type", "string")
                                    putJsonArray("enum") { add("verify"); add("refuse"); add("exit") }
                                }
                            }
                            putJsonArray("required") { add("text"); add("kind") }
                        }
                    }
                }
                putJsonArray("required") { add("warning"); add("replies") }
            }
            put("temperature", 0.3)
            put("maxOutputTokens", 400)
            putJsonObject("thinkingConfig") { put("thinkingBudget", 0) } // OFF: latency + token budget (ADR-0002)
        }
    }.toString()

    private fun parseCoaching(response: String): CoachingResponse? =
        extractInnerJson(response)?.let { CoachingWireParser.parse(it) }

    // --- Grounded classification (ADR-0003, Call A): live web search to identify the CURRENT scam
    // variant. gemini-2.5-flash + google_search, NO responseSchema (that combo is 400 on 2.5, and
    // grounding is 429/paid on Gemini-3 — probed 2026-07-08), so output is JSON-in-text parsed
    // tolerantly. Advisory only; HybridAlert enforces that it can raise but never lower the alert
    // and that scamType/benign need a cited source. Fails closed (null) like everything else here.

    /**
     * Classifies the call against live web intelligence. [callerContext] should be the caller's
     * recent lines only (not our suggested replies) — minimizing what reaches the search index.
     * Returns null on any failure; the caller treats that as "no grounded assessment this turn".
     */
    fun classify(callerContext: String): GroundedAssessment? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || callerContext.isBlank()) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildClassifyRequestBody(callerContext)) ?: return null
            CoachingWireParser.parseGroundedAssessment(extractInnerJson(response), extractSources(response))
        } catch (e: Exception) {
            // DIAGNOSTIC (temporary): type + message only — never the key, URL, or transcript.
            Log.w("GeminiClient", "classify failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildClassifyRequestBody(callerContext: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", GroundedClassifyPrompt.INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    // Caller lines embedded as JSON-escaped data (same injection-safe contract as coach()).
                    addJsonObject { put("text", "What the caller said (untrusted call audio):\n$callerContext") }
                }
            }
        }
        // The grounding tool. NOTE: deliberately NO responseMimeType/responseSchema — unsupported
        // alongside tools on this model (HTTP 400). Output is parsed leniently instead.
        putJsonArray("tools") { addJsonObject { putJsonObject("google_search") { } } }
        putJsonObject("generationConfig") {
            put("temperature", 0.2)
            // 1024, not 300: grounding spends output budget on the search process, and the prompt asks
            // for one grounded sentence before the JSON (which is what makes Gemini attach citation
            // chunks) — 300 truncated the JSON mid-object (probed 2026-07-08).
            put("maxOutputTokens", 1024)
        }
    }.toString()

    // --- Free-form "Ask VAARTA" chat (v2, spec §6.5). Web-grounded prose (like classify(): tools +
    // NO responseSchema, unsupported together on 2.5). Safety is the ChatPrompt system instruction +
    // fail-closed; the reply is NOT run through SuggestionSafetyFilter (that deny-list is tuned for
    // short imperative coaching suggestions and would wrongly reject educational prose). Blocking —
    // call off the main thread. Returns null on any failure; the caller shows a safe fallback line.

    /**
     * Answers a free-form chat message. [context] is an optional situation summary (null for a blank
     * chat); [history] is the prior user/VAARTA turns; [userText] the new message. Returns prose +
     * any cited sources, or null on failure.
     */
    fun chat(
        context: String?,
        history: List<ChatMessage>,
        userText: String,
        attachments: List<ChatAttachment> = emptyList(),
    ): ChatAnswer? {
        val key = BuildConfig.GEMINI_API_KEY
        // Allow an attachment with no words ("what is this?"), but reject a fully empty send, an
        // oversized clip, or a missing key — all fail closed.
        if (key.isBlank() || (userText.isBlank() && attachments.isEmpty())) return null
        if (attachments.sumOf { it.bytes.size } > MAX_INLINE_AUDIO_BYTES) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildChatRequestBody(context, history, userText, attachments), CHAT_TIMEOUT_MS) ?: return null
            val text = extractText(response)?.trim().orEmpty()
            if (text.isEmpty()) null else ChatAnswer(text, extractSources(response))
        } catch (e: Exception) {
            // DIAGNOSTIC (temporary): type + message only — never the key, URL, or the user's words.
            Log.w("GeminiClient", "chat failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildChatRequestBody(
        context: String?,
        history: List<ChatMessage>,
        userText: String,
        attachments: List<ChatAttachment>,
    ): String = buildJsonObject {
        putJsonObject("system_instruction") {
            val withContext = if (context.isNullOrBlank()) {
                ChatPrompt.INSTRUCTION
            } else {
                // Context (e.g. a saved call's verdict/transcript) is DATA the user is asking about,
                // never instructions — framed as such, same contract as the other paths here.
                ChatPrompt.INSTRUCTION + "\n\nThe user is asking about this (untrusted) context:\n" + context
            }
            // Language directive goes LAST (after any context) so recency pins the reply language.
            val instruction = withContext + "\n\n" + ChatPrompt.LANGUAGE_REMINDER
            putJsonArray("parts") { addJsonObject { put("text", instruction) } }
        }
        putJsonArray("contents") {
            for (m in history) {
                addJsonObject {
                    put("role", if (m.fromUser) "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", m.text) } }
                }
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    // Text first; attachments (untrusted media the user wants analyzed) as inline_data.
                    val prompt = userText.ifBlank { "Please look at what I attached — is it a scam? What should I do?" }
                    addJsonObject { put("text", prompt) }
                    for (a in attachments) {
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", a.mimeType)
                                put("data", Base64.getEncoder().encodeToString(a.bytes))
                            }
                        }
                    }
                }
            }
        }
        putJsonArray("tools") { addJsonObject { putJsonObject("google_search") { } } }
        putJsonObject("generationConfig") {
            put("temperature", 0.4)
            put("maxOutputTokens", 1024)
        }
    }.toString()

    // --- Home education feed (v2, spec §6.1). Both calls are web-grounded (google_search, NO
    // responseSchema — unsupported together on 2.5, same as classify()/chat()) and fail closed: the
    // feed falls back to the bundled seed, the summary falls back to the card's one-line. Grounded web
    // results are DATA (AwarenessPrompt frames them so), never instructions.

    /**
     * Fetches current India scam-awareness cards from the web. Returns null on any failure or if
     * nothing usable parses — the caller then shows the cached feed or the bundled seed (never empty).
     * Blocking — call off the main thread.
     */
    fun awarenessFeed(): List<AwarenessCard>? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildGroundedBody(AwarenessPrompt.FEED, "List the scams Indians are being targeted with right now."), CHAT_TIMEOUT_MS) ?: return null
            AwarenessWireParser.parseFeed(extractText(response)).ifEmpty { null }
        } catch (e: Exception) {
            Log.w("GeminiClient", "awarenessFeed failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Summarizes one scam topic in plain language, grounded on current web sources. [title]/[scamType]
     * are OUR trusted labels (from a card), never user/model free text. Returns prose + the real cited
     * [Source]s, or null on failure — the caller falls back to the card's one-line. Blocking.
     */
    fun summarizeArticle(title: String, scamType: String): ArticleSummary? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || title.isBlank()) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildGroundedBody(AwarenessPrompt.SUMMARY_SYSTEM, AwarenessPrompt.summaryQuery(title, scamType)), CHAT_TIMEOUT_MS) ?: return null
            val text = extractText(response)?.trim().orEmpty()
            if (text.isEmpty()) null else ArticleSummary(text, extractSources(response))
        } catch (e: Exception) {
            Log.w("GeminiClient", "summarizeArticle failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Shared grounded request: a system instruction + one fixed user line + google_search, no schema. */
    private fun buildGroundedBody(systemInstruction: String, userLine: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", userLine) } }
            }
        }
        putJsonArray("tools") { addJsonObject { putJsonObject("google_search") { } } }
        putJsonObject("generationConfig") {
            put("temperature", 0.3)
            put("maxOutputTokens", 1024)
        }
    }.toString()

    // --- Recorded-call analysis (ADR-0003 Phase 4D): a whole audio clip -> diarized transcript +
    // scam classification, in ONE generateContent call. Same fails-closed contract as everything else
    // here (missing key / oversized clip / network error / non-200 / unparseable -> null). The audio
    // is embedded as base64 inline_data; the model is told (AudioAnalyzePrompt) it is UNTRUSTED and
    // must be analyzed, never obeyed. Parsing/validation lives in core:reasoning's CoachingWireParser
    // (pure, unit-testable). The returned concern is ADVISORY: HybridAlert lets it raise the alert but
    // the authoritative score comes from replaying the transcript through the deterministic engine.
    // Gate A (2026-07-09) proved this path works on the free tier (HTTP 200, accurate transcript +
    // correct classification of a synthetic digital-arrest clip).

    /**
     * Transcribes, diarizes and classifies a recorded call. [bytes] is the raw audio; [mimeType] its
     * content type (e.g. "audio/wav", "audio/mp4"). Returns null on any failure — the caller shows
     * "couldn't analyze" rather than a fabricated verdict. Blocking — call off the main thread.
     * Clips over [MAX_INLINE_AUDIO_BYTES] are rejected here (null) rather than sent and 4xx'd.
     */
    fun analyzeAudio(bytes: ByteArray, mimeType: String): AudioAnalysis? {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || bytes.isEmpty() || bytes.size > MAX_INLINE_AUDIO_BYTES) return null
        return try {
            val response = post("$ENDPOINT?key=$key", buildAnalyzeAudioBody(bytes, mimeType), AUDIO_TIMEOUT_MS) ?: return null
            CoachingWireParser.parseAudioAnalysis(extractInnerJson(response))
        } catch (e: Exception) {
            // DIAGNOSTIC (temporary): type + message only — never the key, URL, or transcript.
            Log.w("GeminiClient", "analyzeAudio failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun buildAnalyzeAudioBody(bytes: ByteArray, mimeType: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", AudioAnalyzePrompt.INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", "Analyze this recorded phone call.") }
                    addJsonObject {
                        // inline_data carries the whole clip as base64; the request stays under the
                        // ~20 MB generateContent cap because analyzeAudio() rejects larger inputs first.
                        putJsonObject("inline_data") {
                            put("mime_type", mimeType)
                            put("data", Base64.getEncoder().encodeToString(bytes))
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("turns") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("speaker") {
                                    put("type", "string")
                                    putJsonArray("enum") { add("CALLER"); add("USER"); add("UNKNOWN") }
                                }
                                putJsonObject("text") { put("type", "string") }
                            }
                            putJsonArray("required") { add("speaker"); add("text") }
                        }
                    }
                    // Enum-constrain concern so it maps cleanly to RiskLevel — Gate A showed that
                    // WITHOUT this the model returns a free-text description here instead of a level.
                    putJsonObject("concern") {
                        put("type", "string")
                        putJsonArray("enum") { add("OBSERVING"); add("CAUTION"); add("HIGH_RISK"); add("SCAM_PATTERN") }
                    }
                    putJsonObject("summary") { put("type", "string") }
                    putJsonObject("benign") { put("type", "boolean") }
                    putJsonObject("language") { put("type", "string") }
                }
                putJsonArray("required") { add("turns"); add("concern"); add("summary"); add("benign") }
            }
            put("temperature", 0.2)
            put("maxOutputTokens", 2048)
            putJsonObject("thinkingConfig") { put("thinkingBudget", 0) } // OFF: latency + token budget (ADR-0002)
        }
    }.toString()

    /** Cited sources from Gemini's grounding metadata: candidates[0].groundingMetadata.groundingChunks[].web. */
    private fun extractSources(response: String): List<Source> = runCatching {
        json.parseToJsonElement(response)
            .jsonObject["candidates"]?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("groundingMetadata")?.jsonObject?.get("groundingChunks")?.jsonArray
            ?.mapNotNull { chunk ->
                val web = chunk.jsonObject["web"]?.jsonObject ?: return@mapNotNull null
                val uri = web["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = web["title"]?.jsonPrimitive?.content ?: uri
                Source(title, uri)
            }
            ?.distinctBy { it.uri }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** Shared double-parse step: the model's JSON is nested as a string inside
     *  candidates[0].content.parts[0].text — every response shape (suggest/coach) unwraps the same way. */
    private fun extractInnerJson(response: String): String? = runCatching {
        json.parseToJsonElement(response)
            .jsonObject["candidates"]?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
    }.getOrNull()

    /** Plain prose from a (grounded) response: joins every text part in candidates[0].content.parts. */
    private fun extractText(response: String): String? = runCatching {
        json.parseToJsonElement(response)
            .jsonObject["candidates"]?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
