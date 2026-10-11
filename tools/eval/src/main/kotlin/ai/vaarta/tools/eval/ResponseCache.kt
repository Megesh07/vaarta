package ai.vaarta.tools.eval

import java.io.File
import java.security.MessageDigest

fun cacheKey(judgeId: String, promptVersion: String, caseId: String, transcript: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val payload = "$judgeId|$promptVersion|$EVAL_VERSION|$caseId|$transcript"
    return md.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Disk cache of raw judge responses. A re-run reads answered cases from here and makes zero calls. */
class ResponseCache(private val dir: File) {
    init { dir.mkdirs() }

    private fun fileFor(key: String) = File(dir, "$key.json")

    fun get(key: String): String? = fileFor(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun put(key: String, value: String) {
        fileFor(key).writeText(value, Charsets.UTF_8)
    }
}
