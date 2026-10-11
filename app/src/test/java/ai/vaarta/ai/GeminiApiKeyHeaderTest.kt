package ai.vaarta.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL

class GeminiApiKeyHeaderTest {
    @Test
    fun `Gemini REST key is sent in header and omitted from URL`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()

            val connection = URL(server.url("/generateContent").toString()).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setGeminiApiKey("test-api-key")
            connection.outputStream.use { it.write("{}".toByteArray()) }
            connection.inputStream.close()

            val request = server.takeRequest()
            assertEquals("test-api-key", request.getHeader("x-goog-api-key"))
            assertNull(request.requestUrl!!.queryParameter("key"))
            connection.disconnect()
        }
    }
}
