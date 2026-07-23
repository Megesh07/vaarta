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
 *  Caches raw responses and throttles to respect the free-tier RPM. Fails closed on any HTTP/network error. */
class GeminiClassifyJudge(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val throttle: Throttle,
    private val cache: ResponseCache,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS).build(),
) : ScamJudge {
    override val id: String = "gemini-classify:$model"
    override val promptVersion: String = "grounded-classify-v1"

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    override fun assess(case: TranscriptCase): Verdict {
        val key = cacheKey(id, promptVersion, case.id, case.transcript)
        cache.get(key)?.let { return parseClassifyVerdict(id, it, 0) }  // cached: zero calls, latency N/A

        throttle.acquire()
        val body = buildBody(case.transcript)
        val req = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val started = System.currentTimeMillis()
        return try {
            http.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                val latency = System.currentTimeMillis() - started
                if (!resp.isSuccessful) {
                    return Verdict(id, null, null, "", null, latency, respBody, "http ${resp.code}")
                }
                cache.put(key, respBody)
                parseClassifyVerdict(id, respBody, latency)
            }
        } catch (e: Exception) {
            Verdict(id, null, null, "", null, System.currentTimeMillis() - started, "", "net: ${e.javaClass.simpleName}")
        }
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
        }
    }.toString()
}
