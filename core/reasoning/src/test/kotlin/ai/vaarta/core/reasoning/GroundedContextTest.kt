package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroundedContextTest {

    @Test
    fun `null scam type produces an explicit empty context line`() {
        assertEquals(
            "[CONTEXT] Grounded classification so far: none yet.",
            groundedContextLine(null),
        )
    }

    @Test
    fun `blank scam type is treated the same as null`() {
        assertEquals(
            "[CONTEXT] Grounded classification so far: none yet.",
            groundedContextLine("  "),
        )
    }

    @Test
    fun `a source-backed scam type is embedded as advisory context`() {
        assertEquals(
            """[CONTEXT] Grounded classification so far: "UPI wrong payment refund scam" (source-backed, advisory only — reason from the transcript itself).""",
            groundedContextLine("UPI wrong payment refund scam"),
        )
    }
}
