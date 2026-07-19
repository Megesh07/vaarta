package ai.vaarta.ai

import ai.vaarta.BuildConfig
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Checks a URL against free threat intelligence. Fails closed: any network/parse error → UNKNOWN
 * (the UI says nothing). Never reassures — a not-listed URL is CLEAN_SO_FAR, phrased as "not on
 * known threat lists", never "safe". Never touches the risk score (spec §13 safety invariants).
 *
 * KNOWN GAP (flagged during Task 3 implementation, not fixed — see PROJECT_STATUS.md): abuse.ch's
 * current URLhaus docs require an `Auth-Key` header on every request, including this single-URL
 * lookup, which this object does not send (the plan called for URLhaus "no key" — that's no longer
 * accurate). Unauthenticated calls will most likely get rejected (non-200) and fall through to
 * Verdict.UNKNOWN — safe (fail-closed, never crashes, never falsely reassures) but means URLhaus
 * currently contributes nothing in practice until a free Auth-Key is obtained and wired in.
 */
object LinkChecker {
    enum class Verdict { MALICIOUS, CLEAN_SO_FAR, UNKNOWN }

    private const val TIMEOUT_MS = 6_000
    private const val URLHAUS = "https://urlhaus-api.abuse.ch/v1/url/"
    private const val SAFE_BROWSING =
        "https://safebrowsing.googleapis.com/v4/threatMatches:find?key="

    fun check(url: String): Verdict {
        val viaHaus = urlhaus(url)
        if (viaHaus == Verdict.MALICIOUS) return Verdict.MALICIOUS
        val viaSb = safeBrowsing(url)
        return when {
            viaSb == Verdict.MALICIOUS -> Verdict.MALICIOUS
            viaHaus == Verdict.CLEAN_SO_FAR || viaSb == Verdict.CLEAN_SO_FAR -> Verdict.CLEAN_SO_FAR
            else -> Verdict.UNKNOWN
        }
    }

    private fun urlhaus(url: String): Verdict {
        return try {
            val conn = (URL(URLHAUS).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write("url=${URLEncoder.encode(url, "UTF-8")}".toByteArray()) }
            if (conn.responseCode != 200) return Verdict.UNKNOWN
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val status = JSONObject(body).optString("query_status")
            when {
                JSONObject(body).optString("threat").isNotEmpty() -> Verdict.MALICIOUS
                status == "no_results" -> Verdict.CLEAN_SO_FAR
                else -> Verdict.UNKNOWN
            }
        } catch (e: Exception) {
            Log.w("LinkChecker", "urlhaus failed: ${e.javaClass.simpleName}"); Verdict.UNKNOWN
        }
    }

    private fun safeBrowsing(url: String): Verdict {
        val key = BuildConfig.SAFE_BROWSING_API_KEY
        if (key.isBlank()) return Verdict.UNKNOWN
        return try {
            val conn = (URL(SAFE_BROWSING + key).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """
                {"client":{"clientId":"vaarta","clientVersion":"1.0"},
                 "threatInfo":{"threatTypes":["MALWARE","SOCIAL_ENGINEERING"],
                 "platformTypes":["ANY_PLATFORM"],"threatEntryTypes":["URL"],
                 "threatEntries":[{"url":${JSONObject.quote(url)}}]}}
            """.trimIndent()
            conn.outputStream.use { it.write(payload.toByteArray()) }
            if (conn.responseCode != 200) return Verdict.UNKNOWN
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (JSONObject(body).has("matches")) Verdict.MALICIOUS else Verdict.CLEAN_SO_FAR
        } catch (e: Exception) {
            Log.w("LinkChecker", "safebrowsing failed: ${e.javaClass.simpleName}"); Verdict.UNKNOWN
        }
    }
}
