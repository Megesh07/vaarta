package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AwarenessWireParserTest {

    @Test fun `well-formed array parses into cards`() {
        val text = """
            [
              {"title":"Digital arrest scam","oneLine":"Fake police video-call you to extort money.","scamType":"Digital arrest","sourceName":"The Hindu"},
              {"title":"FedEx parcel scam","oneLine":"A caller says your parcel has drugs.","scamType":"Courier"}
            ]
        """.trimIndent()
        val cards = AwarenessWireParser.parseFeed(text)
        assertEquals(2, cards.size)
        assertEquals("Digital arrest scam", cards[0].title)
        assertEquals("The Hindu", cards[0].sourceName)
        assertEquals("", cards[1].sourceName) // missing field tolerated
    }

    @Test fun `array wrapped in prose and markdown fence is still extracted`() {
        val text = "Here are the latest scams:\n```json\n[{\"title\":\"KYC scam\",\"oneLine\":\"Update KYC or account blocked.\"}]\n```\nHope that helps."
        val cards = AwarenessWireParser.parseFeed(text)
        assertEquals(1, cards.size)
        assertEquals("KYC scam", cards[0].title)
    }

    @Test fun `citation markers in prose do not break array extraction`() {
        // Grounded output often sprinkles [1], [2] before the real JSON array — the extractor must
        // skip those and lock onto the first '[' that actually starts an array of objects.
        val text = "Recent reports [1] and advisories [2] describe these scams:\n" +
            "[{\"title\":\"Digital arrest\",\"oneLine\":\"Fake officers extort you [3].\",\"scamType\":\"Arrest\"}]"
        val cards = AwarenessWireParser.parseFeed(text)
        assertEquals(1, cards.size)
        assertEquals("Digital arrest", cards[0].title)
    }

    @Test fun `cards missing title or oneLine are dropped`() {
        val text = """[{"title":"","oneLine":"x"},{"title":"y","oneLine":""},{"title":"Loan app","oneLine":"Harassment over a small loan."}]"""
        val cards = AwarenessWireParser.parseFeed(text)
        assertEquals(1, cards.size)
        assertEquals("Loan app", cards[0].title)
    }

    @Test fun `duplicate titles are collapsed case-insensitively`() {
        val text = """[{"title":"KYC","oneLine":"a"},{"title":"kyc","oneLine":"b"}]"""
        assertEquals(1, AwarenessWireParser.parseFeed(text).size)
    }

    @Test fun `feed is capped at MAX_CARDS`() {
        val items = (1..20).joinToString(",") { """{"title":"Scam $it","oneLine":"line $it"}""" }
        val cards = AwarenessWireParser.parseFeed("[$items]")
        assertEquals(AwarenessWireParser.MAX_CARDS, cards.size)
    }

    @Test fun `malformed json fails closed to empty`() {
        assertTrue(AwarenessWireParser.parseFeed("[{not valid json").isEmpty())
        assertTrue(AwarenessWireParser.parseFeed("no array here").isEmpty())
        assertTrue(AwarenessWireParser.parseFeed(null).isEmpty())
        assertTrue(AwarenessWireParser.parseFeed("").isEmpty())
    }
}
