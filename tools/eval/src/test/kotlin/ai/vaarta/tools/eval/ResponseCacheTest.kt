package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.io.File

class ResponseCacheTest {
    @Test
    fun `key is stable for same inputs and differs when transcript changes`() {
        val a = cacheKey("gemini", "gc-1", "da-001", "hello")
        val b = cacheKey("gemini", "gc-1", "da-001", "hello")
        val c = cacheKey("gemini", "gc-1", "da-001", "HELLO")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `put then get round-trips and missing key is null`(@TempDir tmp: File) {
        val cache = ResponseCache(tmp)
        assertNull(cache.get("k1"))
        cache.put("k1", "{\"x\":1}")
        assertEquals("{\"x\":1}", cache.get("k1"))
    }
}
