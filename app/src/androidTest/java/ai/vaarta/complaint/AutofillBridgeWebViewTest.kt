package ai.vaarta.complaint

import ai.vaarta.core.complaint.SlotSource
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class AutofillBridgeWebViewTest {

    private fun field(key: String, value: String, selector: String) =
        FilledField(key, key, value, SlotSource.DETECTED, selector, null)

    @Test
    fun fillsMappedFieldsAndNeverSubmits() = runBlocking {
        val instr = InstrumentationRegistry.getInstrumentation()
        lateinit var web: WebView

        // WebView must be created + driven on the main thread.
        instr.runOnMainSync { web = WebView(instr.targetContext).apply { settings.javaScriptEnabled = true } }

        // Deliberately NOT web.loadUrl("file:///android_asset/mock_ncrp.html"): instrumented tests run
        // INSIDE the target app's process (Android's shared-process instrumentation model), and
        // WebView's android_asset resolution goes through Context.getApplicationContext() — which in
        // that shared process resolves to the target app's Application no matter which Context wraps
        // the WebView. mock_ncrp.html packages into the separate test APK (app/src/androidTest/assets),
        // so file:///android_asset/ can never see it there; it fails with net::ERR_FILE_NOT_FOUND
        // (confirmed via logcat while debugging this test, with both instr.context and
        // instr.targetContext). Reading the asset bytes directly from the instrumentation's own
        // context — which does NOT go through getApplicationContext() — and feeding them to the
        // WebView via loadDataWithBaseURL sidesteps the issue entirely.
        val html = instr.context.assets.open("mock_ncrp.html").bufferedReader().use { it.readText() }
        loadAndWait(instr, web, html)

        val fields = listOf(
            field("incident.description", "A".repeat(200), "textarea#incidentDesc"),
            field("suspect.mobile", "+919812345678", "input#suspectMobile"),
        )
        val js = AutofillBridge.buildFillJs(fields)
        eval(instr, web, js)

        assertEquals("\"+919812345678\"", eval(instr, web, "document.querySelector('input#suspectMobile').value"))
        // evaluateJavascript's callback always returns a JSON-encoded value, so a JS string result
        // comes back double-quoted (as above) — "false" alone (no wrapping quotes) would never match.
        assertEquals("\"false\"", eval(instr, web, "String(window.__submitted)"))
    }

    private suspend fun loadAndWait(instr: android.app.Instrumentation, web: WebView, html: String) =
        withTimeout(10_000) {
            suspendCancellableCoroutine<Unit> { cont ->
                instr.runOnMainSync {
                    web.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, u: String?) { if (cont.isActive) cont.resume(Unit) }
                    }
                    web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                }
            }
        }

    private suspend fun eval(instr: android.app.Instrumentation, web: WebView, js: String): String =
        withTimeout(10_000) {
            suspendCancellableCoroutine { cont ->
                instr.runOnMainSync { web.evaluateJavascript(js) { if (cont.isActive) cont.resume(it) } }
            }
        }
}
