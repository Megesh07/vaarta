package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pure parser tests for [OgTagParser] — the real-article headline/image/publisher lift used to make
 * the live scam-news feed show each article's own photo instead of a generic illustration
 * (2026-07-21). No network; every case is a raw HTML snippet.
 */
class OgTagParserTest {

    @Test
    fun `extracts og title image and site name`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Mumbai man loses Rs 12 lakh in digital arrest scam">
            <meta property="og:image" content="https://cdn.ndtv.com/story/12345.jpg">
            <meta property="og:site_name" content="NDTV">
            </head><body>...</body></html>
        """.trimIndent()
        val og = OgTagParser.parse(html)
        assertEquals("Mumbai man loses Rs 12 lakh in digital arrest scam", og?.title)
        assertEquals("https://cdn.ndtv.com/story/12345.jpg", og?.imageUrl)
        assertEquals("NDTV", og?.siteName)
    }

    @Test
    fun `tolerates reversed attribute order and single quotes`() {
        val html = "<meta content='https://img.thehindu.com/a.png' property='og:image'/>"
        assertEquals("https://img.thehindu.com/a.png", OgTagParser.parse(html)?.imageUrl)
    }

    @Test
    fun `falls back to twitter image when og image is absent`() {
        val html = """<meta name="twitter:image" content="https://x.com/y.jpg">"""
        assertEquals("https://x.com/y.jpg", OgTagParser.parse(html)?.imageUrl)
    }

    @Test
    fun `decodes ampersands in image query strings`() {
        val html = """<meta property="og:image" content="https://cdn.site.com/i.jpg?w=1200&amp;h=630">"""
        assertEquals("https://cdn.site.com/i.jpg?w=1200&h=630", OgTagParser.parse(html)?.imageUrl)
    }

    @Test
    fun `drops a relative or non-http image`() {
        val html = """
            <meta property="og:image" content="/local/relative.jpg">
            <meta property="og:title" content="A headline">
        """.trimIndent()
        val og = OgTagParser.parse(html)
        assertNull(og?.imageUrl)          // relative image rejected (never a broken img)
        assertEquals("A headline", og?.title) // but the title still comes through
    }

    @Test
    fun `returns null when no og or twitter tags are present`() {
        assertNull(OgTagParser.parse("<html><head><title>plain</title></head></html>"))
        assertNull(OgTagParser.parse(""))
        assertNull(OgTagParser.parse(null))
    }
}
