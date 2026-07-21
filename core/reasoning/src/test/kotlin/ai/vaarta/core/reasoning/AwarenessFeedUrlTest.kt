package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the live-news feed contract (2026-07-21): real article URLs come ONLY from grounding
 * [Source]s (never model-typed text), matched topically where possible and positionally filled so
 * cards still get a real link. Grounding URIs here are Google redirect links (as they are live).
 */
class AwarenessFeedUrlTest {

    private val feedJson = """
        [
          {"title":"Digital arrest scam","oneLine":"Fake CBI officers threaten arrest.","scamType":"Digital arrest","sourceName":"NDTV"},
          {"title":"Courier parcel scam","oneLine":"Fake FedEx call about a seized parcel.","scamType":"Courier","sourceName":"The Hindu"}
        ]
    """.trimIndent()

    private val sources = listOf(
        Source("ndtv.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AAA"),
        Source("thehindu.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/BBB"),
    )

    @Test
    fun `attaches real grounding urls to matching cards by publisher`() {
        val cards = AwarenessWireParser.parseFeed(feedJson, sources)
        assertEquals(2, cards.size)
        val digital = cards.first { it.scamType == "Digital arrest" }
        val courier = cards.first { it.scamType == "Courier" }
        assertEquals("https://vertexaisearch.cloud.google.com/grounding-api-redirect/AAA", digital.url)
        assertEquals("https://vertexaisearch.cloud.google.com/grounding-api-redirect/BBB", courier.url)
    }

    @Test
    fun `never invents a url when there are no grounding sources`() {
        val cards = AwarenessWireParser.parseFeed(feedJson, emptyList())
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.url == null }, "cards must have no URL when grounding gave none")
    }

    @Test
    fun `positionally fills a real url when text matching cannot align`() {
        // Sources whose titles share NO topical/publisher tokens with the cards → pass 1 finds
        // nothing, pass 2 assigns the real links in order so every card still opens a real article.
        val opaque = listOf(
            Source("zzz", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/P1"),
            Source("qqq", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/P2"),
        )
        val cards = AwarenessWireParser.parseFeed(feedJson, opaque)
        assertEquals("https://vertexaisearch.cloud.google.com/grounding-api-redirect/P1", cards[0].url)
        assertEquals("https://vertexaisearch.cloud.google.com/grounding-api-redirect/P2", cards[1].url)
    }

    @Test
    fun `cardsFromSources keeps real headlines and drops bare-domain homepages`() {
        val sources = listOf(
            Source("Boss Scam Alert: I4C Flags CEO Impersonation Fraud", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/A"),
            Source("youtube.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/B"),
            Source("axis.bank.in", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/C"),
            Source("Two people lose Rs 1 crore in digital arrest fraud", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/D"),
        )
        val cards = AwarenessWireParser.cardsFromSources(sources)
        assertEquals(2, cards.size, "only the two real headlines should survive")
        assertTrue(cards.all { it.url != null })
        assertTrue(cards.none { it.title.contains(".com") || it.title.contains(".in") })
    }

    @Test
    fun `leaves extra cards url-null when sources run out`() {
        val oneSource = listOf(Source("ndtv.com", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/AAA"))
        val cards = AwarenessWireParser.parseFeed(feedJson, oneSource)
        assertEquals(2, cards.size)
        assertNotNull(cards.first { it.scamType == "Digital arrest" }.url) // NDTV match wins the one link
        assertNull(cards.first { it.scamType == "Courier" }.url)           // no link left → degrades cleanly
    }
}
