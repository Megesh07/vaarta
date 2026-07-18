package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownTest {
    @Test fun plainTextIsOneParagraph() {
        val b = parseMarkdown("Just plain text.")
        assertEquals(listOf(MdBlock.Paragraph(listOf(MdSpan("Just plain text.")))), b)
    }

    @Test fun boldInlineIsSplitIntoSpans() {
        val b = parseMarkdown("Be **very** careful")
        assertEquals(
            MdBlock.Paragraph(listOf(MdSpan("Be "), MdSpan("very", bold = true), MdSpan(" careful"))),
            b.single(),
        )
    }

    @Test fun italicWithSingleAsterisk() {
        val b = parseMarkdown("this is *important* ok")
        assertEquals(
            MdBlock.Paragraph(listOf(MdSpan("this is "), MdSpan("important", italic = true), MdSpan(" ok"))),
            b.single(),
        )
    }

    @Test fun headingBecomesHeadingBlockWithoutHashes() {
        val b = parseMarkdown("## What to do")
        assertEquals(MdBlock.Heading(listOf(MdSpan("What to do"))), b.single())
    }

    @Test fun dashBulletsAreCollected() {
        val b = parseMarkdown("- first\n- second")
        assertEquals(
            MdBlock.Bullets(listOf(listOf(MdSpan("first")), listOf(MdSpan("second")))),
            b.single(),
        )
    }

    @Test fun numberedListIsCollected() {
        val b = parseMarkdown("1. one\n2. two")
        assertEquals(
            MdBlock.Numbered(listOf(listOf(MdSpan("one")), listOf(MdSpan("two")))),
            b.single(),
        )
    }

    @Test fun noRawMarkersSurviveAnywhere() {
        val src = "# Title\n\nText with **bold**, *em*, and a `code` bit.\n- item **x**\n> quote"
        val flat = parseMarkdown(src).joinToString(" ") { block ->
            when (block) {
                is MdBlock.Paragraph -> block.spans.joinToString("") { it.text }
                is MdBlock.Heading -> block.spans.joinToString("") { it.text }
                is MdBlock.Bullets -> block.items.joinToString(" ") { it.joinToString("") { s -> s.text } }
                is MdBlock.Numbered -> block.items.joinToString(" ") { it.joinToString("") { s -> s.text } }
            }
        }
        assertTrue(!flat.contains("**"), "no ** left")
        assertTrue(!flat.contains("#"), "no leading # left")
        assertTrue(!flat.contains("`"), "no backticks left")
        assertTrue(!flat.trimStart().startsWith(">"), "no blockquote marker left")
    }

    @Test fun blankInputIsEmpty() = assertEquals(emptyList<MdBlock>(), parseMarkdown("   "))
}
