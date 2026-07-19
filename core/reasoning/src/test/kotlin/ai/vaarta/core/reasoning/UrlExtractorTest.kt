package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlExtractorTest {
    @Test
    fun `extracts http and https urls`() {
        assertEquals(
            listOf("http://bit.ly/x", "https://sbi-kyc.example.com/login"),
            UrlExtractor.extract("click http://bit.ly/x or https://sbi-kyc.example.com/login now"),
        )
    }

    @Test
    fun `extracts bare domain with path`() {
        assertTrue(UrlExtractor.extract("go to sbi-verify.co/kyc please").contains("sbi-verify.co/kyc"))
    }

    @Test
    fun `returns empty for plain text`() {
        assertTrue(UrlExtractor.extract("please call me back tomorrow").isEmpty())
    }
}
