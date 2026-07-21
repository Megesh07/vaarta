package ai.vaarta.complaint

import org.json.JSONObject

/**
 * Best-effort autofill over the LIVE portal in the WebView. Builds a JS snippet that sets values on
 * the pack's known selectors and fires input/change events so the page's own validation runs. It never
 * targets a submit, OTP or CAPTCHA control and never calls .click()/.submit() — VAARTA fills, the user
 * submits (Global Constraints). When a selector is missing/stale, the field simply isn't filled here;
 * the UI's tap-to-fill chips are the always-present fallback.
 */
object AutofillBridge {

    fun fillableFields(fields: List<FilledField>): List<FilledField> =
        fields.filter { !it.selector.isNullOrBlank() && it.value.isNotBlank() }

    fun buildFillJs(fields: List<FilledField>): String {
        val ops = fillableFields(fields).joinToString("\n") { field ->
            // JSONObject.quote gives a safe, fully-escaped JS string literal (handles quotes/newlines).
            val sel = JSONObject.quote(field.selector)
            val value = JSONObject.quote(field.value)
            """
            (function(){
              var el = document.querySelector($sel);
              if (el) {
                el.value = $value;
                el.dispatchEvent(new Event('input', {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
              }
            })();
            """.trimIndent()
        }
        return "(function(){$ops})();"
    }
}
