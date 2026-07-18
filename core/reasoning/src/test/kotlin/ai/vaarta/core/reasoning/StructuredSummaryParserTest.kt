package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Redesign spec §7: the structured article summary parses tolerantly (preamble/fences/citations
 * ignored) and fails closed to null so the caller can fall back to prose, then to the one-liner.
 */
class StructuredSummaryParserTest {

    private val good = """
        {"whatItIs": "Fraudsters pose as police and threaten arrest over a video call.",
         "howToSpot": ["They demand you stay on the line", "They ask for money to clear a case"],
         "whatToDo": ["Hang up", "Call 1930"]}
    """.trimIndent()

    @Test
    fun `well-formed object parses`() {
        val s = AwarenessWireParser.parseStructuredSummary(good)!!
        assertEquals("Fraudsters pose as police and threaten arrest over a video call.", s.whatItIs)
        assertEquals(2, s.howToSpot.size)
        assertEquals(listOf("Hang up", "Call 1930"), s.whatToDo)
    }

    @Test
    fun `preamble and markdown fence are ignored`() {
        val wrapped = "Here is the explanation you asked for [1]:\n```json\n$good\n```\nHope this helps!"
        val s = AwarenessWireParser.parseStructuredSummary(wrapped)!!
        assertEquals(2, s.whatToDo.size)
    }

    @Test
    fun `blank list items are dropped`() {
        val s = AwarenessWireParser.parseStructuredSummary(
            """{"whatItIs": "x", "howToSpot": ["  ", "real sign"], "whatToDo": ["do this", ""]}""",
        )!!
        assertEquals(listOf("real sign"), s.howToSpot)
        assertEquals(listOf("do this"), s.whatToDo)
    }

    @Test
    fun `model JSON drift is tolerated - trailing commas`() {
        val drifty = """{"whatItIs": "x", "howToSpot": ["a", "b",], "whatToDo": ["c",],}"""
        val s = AwarenessWireParser.parseStructuredSummary(drifty)!!
        assertEquals(listOf("a", "b"), s.howToSpot)
    }

    @Test
    fun `missing or empty required parts fail closed`() {
        assertNull(AwarenessWireParser.parseStructuredSummary("""{"whatItIs": "", "howToSpot": ["a"], "whatToDo": ["b"]}"""))
        assertNull(AwarenessWireParser.parseStructuredSummary("""{"whatItIs": "x", "howToSpot": [], "whatToDo": ["b"]}"""))
        assertNull(AwarenessWireParser.parseStructuredSummary("""{"whatItIs": "x", "howToSpot": ["a"], "whatToDo": []}"""))
    }

    @Test
    fun `malformed empty and null fail closed`() {
        assertNull(AwarenessWireParser.parseStructuredSummary(null))
        assertNull(AwarenessWireParser.parseStructuredSummary(""))
        assertNull(AwarenessWireParser.parseStructuredSummary("plain prose answer with no json at all"))
        assertNull(AwarenessWireParser.parseStructuredSummary("{broken json"))
    }
}
