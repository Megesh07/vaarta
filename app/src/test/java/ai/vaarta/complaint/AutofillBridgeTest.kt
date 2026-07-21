package ai.vaarta.complaint

import ai.vaarta.core.complaint.SlotSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutofillBridgeTest {

    private fun f(key: String, value: String, selector: String?) =
        FilledField(key, key, value, SlotSource.DETECTED, selector, null)

    @Test
    fun `only fields with a selector and a value are fillable`() {
        val fields = listOf(
            f("a", "x", "input#a"),
            f("b", "", "input#b"),      // no value
            f("c", "y", null),          // no selector
        )
        assertEquals(listOf("a"), AutofillBridge.fillableFields(fields).map { it.key })
    }

    @Test
    fun `generated JS targets the selector and escapes the value`() {
        val js = AutofillBridge.buildFillJs(listOf(f("a", "he said \"stop\"\nnow", "input#a")))
        assertTrue(js.contains("querySelector(\"input#a\")"))
        assertFalse(js.contains("\n\"stop\"\nnow\n"), "raw newline would break the script")
        assertTrue(js.contains("dispatchEvent"))
    }

    @Test
    fun `JS never references submit, otp or captcha controls`() {
        val js = AutofillBridge.buildFillJs(listOf(f("a", "x", "input#a"))).lowercase()
        assertFalse(js.contains("submit")); assertFalse(js.contains("otp")); assertFalse(js.contains("captcha"))
    }
}
