package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class GroundedClassifyPromptTest {
    @Test
    fun `instruction names the classify contract fields and stays scam-focused`() {
        val i = GroundedClassifyPrompt.INSTRUCTION
        assertTrue(i.contains("scamType"))
        assertTrue(i.contains("concern"))
        assertTrue(i.contains("benign"))
        assertTrue(i.contains("digital arrest", ignoreCase = true))
    }
}
