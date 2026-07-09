package ai.vaarta.core.reasoning

import ai.vaarta.core.common.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachingSupportTest {

    // --- nextStage ---

    @Test
    fun `nextStage follows the fixed script grammar`() {
        assertEquals(Stage.HOOK, nextStage(Stage.NONE))
        assertEquals(Stage.AUTHORITY, nextStage(Stage.HOOK))
        assertEquals(Stage.ISOLATION, nextStage(Stage.AUTHORITY))
        assertEquals(Stage.ESCALATION, nextStage(Stage.ISOLATION))
        assertEquals(Stage.EXTRACTION, nextStage(Stage.ESCALATION))
    }

    @Test
    fun `nextStage of EXTRACTION stays EXTRACTION`() {
        assertEquals(Stage.EXTRACTION, nextStage(Stage.EXTRACTION))
    }

    // --- Readability (Phase 4A): fragments must join WITHOUT per-fragment trimming ---

    @Test
    fun `assembleTranscript preserves inter-word spaces`() {
        assertEquals("what is your name", assembleTranscript(listOf("what", " is", " your", " name")))
        assertEquals("I am adding my son", assembleTranscript(listOf("I am", " adding", " my son")))
    }

    @Test
    fun `assembleTranscript trims only once on the whole`() {
        assertEquals("hello there", assembleTranscript(listOf("  hello there  ")))
        assertEquals("", assembleTranscript(listOf("   ", "  ")))
    }

    // --- OwnWordsGate: the self-echo defense (ADR-0003 problem 1) ---

    @Test
    fun `flags an exact echo of a recently shown reply`() {
        val gate = OwnWordsGate()
        gate.remember("I will not transfer the money.")
        assertTrue(gate.isLikelyOwnWords("I will not transfer the money"))
    }

    @Test
    fun `flags a near echo tolerant of ASR noise`() {
        val gate = OwnWordsGate()
        gate.remember("Which police station are you calling from?")
        // Realistic ASR garble of the same sentence over a loudspeaker-through-air path.
        assertTrue(gate.isLikelyOwnWords("which police station are you calling form"))
    }

    @Test
    fun `does not flag the caller's independent speech even on overlapping vocabulary`() {
        val gate = OwnWordsGate()
        gate.remember("Which police station are you calling from?")
        // The caller independently says "police" too, but this is a distinctly different sentence.
        assertFalse(gate.isLikelyOwnWords("I am transferring you to the CBI crime branch cyber cell now"))
    }

    @Test
    fun `only remembers the configured number of recent lines`() {
        val gate = OwnWordsGate(rememberCount = 2)
        gate.remember("Which police station are you calling from?")
        gate.remember("I will not transfer any money to that account.")
        gate.remember("I am adding my son to this call right now.")
        assertFalse(gate.isLikelyOwnWords("Which police station are you calling from?"))
        assertTrue(gate.isLikelyOwnWords("I am adding my son to this call right now"))
    }

    @Test
    fun `reset clears remembered lines`() {
        val gate = OwnWordsGate()
        gate.remember("I will not transfer the money.")
        gate.reset()
        assertFalse(gate.isLikelyOwnWords("I will not transfer the money"))
    }

    @Test
    fun `blank input is never flagged`() {
        val gate = OwnWordsGate()
        gate.remember("I will not transfer the money.")
        assertFalse(gate.isLikelyOwnWords(""))
        assertFalse(gate.isLikelyOwnWords("   "))
    }
}
