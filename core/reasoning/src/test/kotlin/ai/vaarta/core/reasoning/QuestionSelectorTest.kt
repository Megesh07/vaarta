package ai.vaarta.core.reasoning

import ai.vaarta.core.common.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestionSelectorTest {

    private val selector = QuestionSelector(PackLoader.fromResource("/packs/core-scam-v1.json"))

    @Test
    fun `no question is offered before any stage is reached`() {
        assertTrue(selector.candidatesFor(Stage.NONE).isEmpty())
        assertEquals(null, selector.select(Stage.NONE, 0))
    }

    @Test
    fun `AUTHORITY stage surfaces only AUTHORITY-level questions`() {
        val ids = selector.candidatesFor(Stage.AUTHORITY).map { it.id }
        assertEquals(listOf("Q_STATION", "Q_VERIFY_1930"), ids)
    }

    @Test
    fun `ISOLATION stage adds isolation questions ranked above authority ones`() {
        val ids = selector.candidatesFor(Stage.ISOLATION).map { it.id }
        assertEquals(listOf("Q_ADD_FAMILY", "Q_STATION", "Q_VERIFY_1930"), ids)
    }

    @Test
    fun `select cycles through candidates and wraps around`() {
        assertEquals("Q_STATION", selector.select(Stage.AUTHORITY, 0)?.id)
        assertEquals("Q_VERIFY_1930", selector.select(Stage.AUTHORITY, 1)?.id)
        assertEquals("Q_STATION", selector.select(Stage.AUTHORITY, 2)?.id)
    }

    @Test
    fun `textFor falls back to any available language when requested one is missing`() {
        val q = selector.candidatesFor(Stage.AUTHORITY).first { it.id == "Q_STATION" }
        assertTrue(selector.textFor(q, "en").contains("police station"))
        assertTrue(selector.textFor(q, "ta").isNotBlank())
    }
}
