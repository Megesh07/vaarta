package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Real news headlines arrive with mixed apostrophe/ampersand spellings; the feed must render them
 *  clean (observed live: raw `&#039;` showing in a card title, 2026-07-21). */
class HtmlEntitiesTest {

    @Test
    fun `decodes numeric entity with a leading zero`() {
        assertEquals("Warns Of 'Boss Scam'", HtmlEntities.decode("Warns Of &#039;Boss Scam&#039;"))
    }

    @Test
    fun `decodes named, plain-numeric and hex entities`() {
        assertEquals("A & B's \"quote\"", HtmlEntities.decode("A &amp; B&#39;s &quot;quote&quot;"))
        assertEquals("it's", HtmlEntities.decode("it&#x27;s"))
        assertEquals("—", HtmlEntities.decode("&mdash;"))
    }

    @Test
    fun `leaves plain text and malformed entities untouched`() {
        assertEquals("plain text 100% ok", HtmlEntities.decode("plain text 100% ok"))
        assertEquals("a &# b", HtmlEntities.decode("a &# b"))
    }
}
