package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatModelsTest {

    @Test fun `short message becomes the title verbatim`() {
        assertEquals("Is this CBI call a scam?", conversationTitleFrom("Is this CBI call a scam?"))
    }

    @Test fun `whitespace is collapsed and trimmed`() {
        assertEquals("hello there friend", conversationTitleFrom("  hello   there\n friend  "))
    }

    @Test fun `long message is truncated with an ellipsis`() {
        val long = "a".repeat(80)
        val title = conversationTitleFrom(long, maxLen = 40)
        assertEquals(40, title.length)
        assertEquals('…', title.last())
    }

    @Test fun `blank message falls back to New chat`() {
        assertEquals("New chat", conversationTitleFrom("   \n  "))
        assertEquals("New chat", conversationTitleFrom(""))
    }
}
