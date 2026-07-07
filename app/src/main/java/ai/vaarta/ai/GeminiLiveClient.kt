package ai.vaarta.ai

import ai.vaarta.BuildConfig
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Streams live microphone audio to the Gemini Live API and emits, in real time, the caller's words
 * (inputTranscription) and a suggested reply (outputTranscription) — the protocol proven headless
 * in `tools:demo:liveProbe` and recorded in ADR-0002.
 *
 * The native-audio model only outputs AUDIO, so we run AUDIO mode + transcriptions and take the
 * TEXT of both; the returned audio is never played (it would leak to the scammer on speaker).
 * Fails closed: no key / connect error / socket error → [onError] and the app stays on the
 * deterministic + Manual path. One instance per live session; call [close] to end it.
 */
class GeminiLiveClient(
    private val onCallerText: (String) -> Unit,   // scammer words (for the deterministic engine)
    private val onSuggestion: (String) -> Unit,   // AI reply suggestion (raw; caller must filter it)
    private val onStatus: (Status) -> Unit,
) {
    enum class Status { CONNECTING, LISTENING, CLOSED, ERROR }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile private var ready = false
    private val suggestionBuffer = StringBuilder()

    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun start() {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            onStatus(Status.ERROR)
            return
        }
        onStatus(Status.CONNECTING)
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    /** Feed a chunk of 16 kHz mono PCM16 mic audio. No-op until the session is ready. */
    fun sendAudio(pcm: ByteArray, length: Int) {
        val ws = webSocket ?: return
        if (!ready) return
        val b64 = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
        ws.send(
            """{"realtimeInput":{"mediaChunks":[{"mimeType":"audio/pcm;rate=16000","data":"$b64"}]}}""",
        )
    }

    fun close() {
        ready = false
        webSocket?.close(1000, "done")
        webSocket = null
        client.dispatcher.executorService.shutdown()
        onStatus(Status.CLOSED)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(SETUP)
        }

        private fun handle(msg: String) {
            when {
                msg.contains("setupComplete") -> {
                    ready = true
                    onStatus(Status.LISTENING)
                }
                else -> {
                    // Caller's words → deterministic engine.
                    INPUT_RE.findAll(msg).forEach { onCallerText(unescape(it.groupValues[1]).trim()) }
                    // AI suggestion streams in fragments → accumulate, flush at turn end. Each
                    // fragment's own leading/trailing space is significant (e.g. "I am" + " a
                    // specialized") — trimming per-fragment here would jam words together, so we
                    // only trim once, on the fully assembled buffer below.
                    OUTPUT_RE.findAll(msg).forEach { suggestionBuffer.append(unescape(it.groupValues[1])) }
                    if (msg.contains("generationComplete") || msg.contains("\"turnComplete\":true")) {
                        val full = suggestionBuffer.toString().trim()
                        suggestionBuffer.clear()
                        if (full.isNotEmpty()) onSuggestion(full)
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w("GeminiLiveClient", "ws failure: ${t.javaClass.simpleName}: ${t.message} http=${response?.code}")
            ready = false
            onStatus(Status.ERROR)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w("GeminiLiveClient", "ws closing $code $reason")
            ready = false
            if (code != 1000) onStatus(Status.ERROR) else onStatus(Status.CLOSED)
        }
    }

    private fun unescape(s: String): String =
        s.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")

    private companion object {
        val INPUT_RE = Regex("\"inputTranscription\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"")
        val OUTPUT_RE = Regex("\"outputTranscription\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"")

        // Proven setup (ADR-0002): native-audio, AUDIO out, in/out transcription, thinking off.
        val SETUP = """
            {"setup":{"model":"models/gemini-2.5-flash-native-audio-latest",
            "generationConfig":{"responseModalities":["AUDIO"],"thinkingConfig":{"thinkingBudget":0}},
            "systemInstruction":{"parts":[{"text":"${SharedScamPrompt.INSTRUCTION.replace("\"", "\\\"").replace("\n", " ")}"}]},
            "outputAudioTranscription":{},"inputAudioTranscription":{}}}
        """.trimIndent().replace("\n", "")
    }
}
