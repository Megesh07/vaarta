package ai.vaarta.tools.eval

import ai.vaarta.core.reasoning.GroundedClassifyPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON = Json { ignoreUnknownKeys = true }

/** Short stable fingerprint of a prompt's text, so any edit to the prompt automatically changes the
 *  cache key — no hand-maintained version bump required, and stale cached responses can never survive
 *  a prompt change. */
internal fun promptFingerprint(text: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(8)
}

/** Pure: turn a raw generateContent body into a Verdict. Fail closed — any parse failure => error verdict. */
internal fun parseClassifyVerdict(judgeId: String, responseBody: String, latencyMs: Long): Verdict {
    return try {
        val text = JSON.parseToJsonElement(responseBody)
            .jsonObject["candidates"]!!.jsonArray[0]
            .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content

        // The grounded reply is one sentence + a JSON object; grab the last {...} block.
        val start = text.lastIndexOf('{')
        val end = text.lastIndexOf('}')
        require(start in 0 until end) { "no JSON object in model text" }
        val obj = JSON.parseToJsonElement(text.substring(start, end + 1)).jsonObject

        val scamType = (obj["scamType"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val concern = (obj["concern"] as? JsonPrimitive)?.content?.let { runCatching { Concern.valueOf(it) }.getOrNull() }
        val benign = (obj["benign"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

        Verdict(
            judgeId = judgeId,
            isScam = benign?.let { !it },   // benign=false => scam-positive
            concern = concern,
            variant = scamType,
            confidence = null,
            latencyMs = latencyMs,
            raw = responseBody,
            error = null,
        )
    } catch (e: Exception) {
        Verdict(judgeId, null, null, "", null, latencyMs, responseBody, "parse: ${e.javaClass.simpleName}: ${e.message}")
    }
}

/** Replicates the production classify() request: GroundedClassifyPrompt + google_search + lenient parse.
 *  Caches raw responses and throttles to respect the free-tier RPM. Fails closed on any HTTP/network error.
 *
 *  Each Gemini free-tier key is capped at 20 requests/day/model (confirmed via the API's own
 *  RESOURCE_EXHAUSTED response, not the documented RPM figure — that cap is far more binding than
 *  the RPM throttle for a 184-case run). apiKeys lets several independently-quota'd keys
 *  (separate AI Studio projects) be round-robined: on an HTTP 429 the judge marks that key
 *  exhausted for the rest of this run and retries the same case on the next key, so one key's
 *  daily wall doesn't stop the whole benchmark. */
class GeminiClassifyJudge(
    private val apiKeys: List<String>,
    private val model: String = "gemini-2.5-flash",
    private val throttle: Throttle,
    private val cache: ResponseCache,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS).build(),
    private val endpoint: String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
) : ScamJudge {
    init { require(apiKeys.isNotEmpty()) { "GeminiClassifyJudge needs at least one API key" } }

    override val id: String = "gemini-classify:$model"
    override val promptVersion: String = "grounded-classify-v1:" + promptFingerprint(GroundedClassifyPrompt.INSTRUCTION)

    // Persists across assess() calls within one run: once a key 429s it stays marked exhausted,
    // so later cases skip straight past it instead of re-spending a throttle wait on a dead key.
    private var nextKeyIndex = 0
    private val exhaustedKeyIndices = mutableSetOf<Int>()

    override fun assess(case: TranscriptCase): Verdict {
        val key = cacheKey(id, promptVersion, case.id, case.transcript)
        cache.get(key)?.let { return parseClassifyVerdict(id, it, 0) }  // cached: zero calls, latency N/A

        val body = buildBody(case.transcript)
        var lastVerdict: Verdict? = null

        repeat(apiKeys.size) {
            val keyIndex = nextKeyIndex
            nextKeyIndex = (nextKeyIndex + 1) % apiKeys.size
            if (keyIndex in exhaustedKeyIndices) return@repeat

            throttle.acquire()
            val req = Request.Builder()
                .url(endpoint)
                .header("x-goog-api-key", apiKeys[keyIndex])
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val started = System.currentTimeMillis()
            val verdict = try {
                http.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    val latency = System.currentTimeMillis() - started
                    if (resp.isSuccessful) {
                        val parsed = parseClassifyVerdict(id, respBody, latency)
                        // Only cache a genuinely usable answer — caching an unparseable body would
                        // permanently poison this case (a future run replays the same broken text
                        // forever instead of getting a fresh, possibly-fixed-by-config generation).
                        if (parsed.error == null) cache.put(key, respBody)
                        parsed
                    } else {
                        Verdict(id, null, null, "", null, latency, respBody, "http ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Verdict(id, null, null, "", null, System.currentTimeMillis() - started, "", "net: ${e.javaClass.simpleName}")
            }

            lastVerdict = verdict
            if (verdict.error == "http 429") {
                exhaustedKeyIndices += keyIndex
                // fall through to the next key in this repeat loop
            } else {
                return verdict  // success or a genuine non-429 failure — don't rotate keys for it
            }
        }
        return lastVerdict ?: Verdict(id, null, null, "", null, 0, "", "http 429")  // every key exhausted
    }

    private fun buildBody(transcript: String): String = buildJsonObject {
        putJsonObject("system_instruction") {
            putJsonArray("parts") { addJsonObject { put("text", GroundedClassifyPrompt.INSTRUCTION) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", "What the caller said (untrusted call audio):\n$transcript") }
                }
            }
        }
        putJsonArray("tools") { addJsonObject { putJsonObject("google_search") {} } }
        putJsonObject("generationConfig") {
            put("temperature", 0.2)
            put("maxOutputTokens", 1024)
            // OFF: without this, the model's internal "thinking" tokens can consume the whole
            // maxOutputTokens budget before it ever reaches the mandatory trailing JSON block —
            // measured directly (2026-07-24): thoughtsTokenCount alone hit 1235 in one real
            // response, exceeding the 1024 cap and leaving zero room for the parseable footer.
            putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
        }
    }.toString()
}
