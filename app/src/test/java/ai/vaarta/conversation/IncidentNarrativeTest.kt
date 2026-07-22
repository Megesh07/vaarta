package ai.vaarta.conversation

import ai.vaarta.ChatItem
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.reasoning.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the 2026-07-22 leak fix: a complaint narrative built from a conversation must include only
 * what the caller said and what the user said happened — never VAARTA's own Assistant/Coach turns,
 * which previously let a fail-closed apology ("I couldn't reach the assistant just now...") end up
 * inside a real incident description sent toward a government complaint form.
 */
class IncidentNarrativeTest {

    @Test
    fun `keeps Caller and You turns, in order`() {
        val items = listOf(
            ChatItem.Caller("This is CBI, you are under digital arrest"),
            ChatItem.You("Please tell me your badge number"),
        )
        val transcript = conversationTranscript(items)
        assertEquals("Caller: This is CBI, you are under digital arrest\nMe: Please tell me your badge number", transcript)
    }

    @Test
    fun `excludes Coach turns from the narrative`() {
        val items = listOf(
            ChatItem.Caller("Transfer the money now"),
            ChatItem.Coach(warning = "Do not transfer any money.", replies = emptyList()),
        )
        val transcript = conversationTranscript(items)
        assertFalse(transcript.contains("Do not transfer"))
        assertTrue(transcript.contains("Transfer the money now"))
    }

    @Test
    fun `excludes Assistant turns, including a fail-closed apology, from the narrative`() {
        val items = listOf(
            ChatItem.Caller("I need your OTP"),
            ChatItem.Assistant("I couldn't reach the assistant just now — please try again."),
        )
        val transcript = conversationTranscript(items)
        assertFalse(transcript.contains("couldn't reach the assistant"))
        assertTrue(transcript.contains("I need your OTP"))
    }

    @Test
    fun `empty turn list produces an empty transcript`() {
        assertEquals("", conversationTranscript(emptyList()))
    }

    @Test
    fun `narrative text prepends the verdict line when a header exists`() {
        val header = ConversationViewModel.CallHeader(
            level = RiskLevel.SCAM_PATTERN, score = 100, scamType = "Digital Arrest", kind = SessionSource.LIVE,
        )
        val text = incidentNarrativeText(header, "Caller: hello")
        assertTrue(text.startsWith("VAARTA's verdict: SCAM_PATTERN (risk 100/100), identified as \"Digital Arrest\".\n\n"))
        assertTrue(text.endsWith("Caller: hello"))
    }

    @Test
    fun `narrative text is just the transcript when there is no header`() {
        assertEquals("Caller: hi", incidentNarrativeText(null, "Caller: hi"))
    }
}
