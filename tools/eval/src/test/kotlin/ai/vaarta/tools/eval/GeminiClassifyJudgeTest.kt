package ai.vaarta.tools.eval

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Exercises the real HTTP round-robin/failover path (via a local MockWebServer, not a hand-rolled
 *  stub) that lets several independently-quota'd Gemini keys share one 184-case benchmark run. */
class GeminiClassifyJudgeTest {
    private val server = MockWebServer()
    private val cannedScamBody =
        """{"candidates":[{"content":{"parts":[{"text":"Recent advisories describe CBI calls.\n{\"scamType\":\"digital-arrest\",\"concern\":\"SCAM_PATTERN\",\"benign\":false}"}]}}]}"""
    private val quotaExceededBody =
        """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"quota exceeded"}}"""

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.shutdown()

    private fun judge(apiKeys: List<String>, cacheDir: File): GeminiClassifyJudge = GeminiClassifyJudge(
        apiKeys = apiKeys,
        throttle = Throttle(minIntervalMs = 0, now = { 0L }, sleep = {}),
        cache = ResponseCache(cacheDir),
        endpoint = server.url("/generateContent").toString(),
    )

    private fun case(id: String) = TranscriptCase(
        id = id,
        transcript = "Caller: this is a test transcript.",
        language = "en",
        groundTruth = GroundTruth(true, "digital-arrest", Concern.SCAM_PATTERN, Difficulty.BLATANT),
        source = "SYNTHETIC: test fixture",
        provenance = Provenance.SYNTHETIC,
    )

    @Test
    fun `rotates to the next key on 429 and returns the successful verdict`(@TempDir tmp: File) {
        server.enqueue(MockResponse().setResponseCode(429).setBody(quotaExceededBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(cannedScamBody))

        val v = judge(listOf("key-1", "key-2"), tmp).assess(case("c-1"))

        assertEquals(true, v.isScam)
        assertNull(v.error)
        assertEquals(2, server.requestCount)
        val firstKey = server.takeRequest().requestUrl!!.queryParameter("key")
        val secondKey = server.takeRequest().requestUrl!!.queryParameter("key")
        assertEquals("key-1", firstKey)
        assertEquals("key-2", secondKey)
    }

    @Test
    fun `all keys exhausted yields a 429 error verdict, never a benign one`(@TempDir tmp: File) {
        server.enqueue(MockResponse().setResponseCode(429).setBody(quotaExceededBody))
        server.enqueue(MockResponse().setResponseCode(429).setBody(quotaExceededBody))

        val v = judge(listOf("key-1", "key-2"), tmp).assess(case("c-1"))

        assertEquals("http 429", v.error)
        assertNull(v.isScam)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a key exhausted on an earlier case is skipped entirely on a later case`(@TempDir tmp: File) {
        server.enqueue(MockResponse().setResponseCode(429).setBody(quotaExceededBody)) // c-1 on key-1
        server.enqueue(MockResponse().setResponseCode(200).setBody(cannedScamBody))    // c-1 on key-2
        server.enqueue(MockResponse().setResponseCode(200).setBody(cannedScamBody))    // c-2 on key-2 directly

        val j = judge(listOf("key-1", "key-2"), tmp)
        j.assess(case("c-1"))
        val before = server.requestCount
        val v2 = j.assess(case("c-2"))

        assertEquals(true, v2.isScam)
        assertEquals(1, server.requestCount - before)  // no wasted retry against the known-dead key-1
        server.takeRequest(); server.takeRequest()
        val thirdRequestKey = server.takeRequest().requestUrl!!.queryParameter("key")
        assertEquals("key-2", thirdRequestKey)
    }

    @Test
    fun `a non-429 error does not rotate keys or get retried`(@TempDir tmp: File) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val v = judge(listOf("key-1", "key-2"), tmp).assess(case("c-1"))

        assertEquals("http 500", v.error)
        assertNull(v.isScam)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `single key behaves exactly as before (backward compatible)`(@TempDir tmp: File) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(cannedScamBody))

        val v = judge(listOf("only-key"), tmp).assess(case("c-1"))

        assertTrue(v.error == null && v.isScam == true)
        assertEquals(1, server.requestCount)
    }
}
