package ai.vaarta.tools.demo

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Headless probe of the Gemini Live BidiGenerateContent WebSocket. Answers, before we build any
 * Android mic/streaming code: does the connection work, does the ONE available Live model
 * (native-audio) accept a TEXT response modality + system instruction, and what does a turn look
 * like on the wire? Run: `gradle :tools:demo:liveProbe` (key read from secrets.properties).
 */
fun main() {
    val key = System.getProperty("gemini.key").orEmpty()
    if (key.isBlank()) {
        println("NO KEY (set GEMINI_API_KEY in secrets.properties)")
        return
    }
    val url = "wss://generativelanguage.googleapis.com/ws/" +
        "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"

    val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived socket
        .build()
    val latch = CountDownLatch(1)

    val systemInstruction =
        "You are VAARTA, a specialized assistant that helps a potential victim safely handle a " +
            "digital-arrest scam call in India. Given what the caller said, reply with ONE short, safe " +
            "sentence the user can say back (a verification question or isolation-breaker). Never tell " +
            "the user to pay or comply. Reply in plain text only."

    // native-audio only supports AUDIO output — but we take its TRANSCRIPTION as text and never
    // play the audio. inputAudioTranscription also gives us the scammer's transcript for the engine.
    val setup = """
        {"setup":{"model":"models/gemini-2.5-flash-native-audio-latest",
        "generationConfig":{"responseModalities":["AUDIO"]},
        "systemInstruction":{"parts":[{"text":"${systemInstruction.replace("\"", "\\\"")}"}]},
        "outputAudioTranscription":{},"inputAudioTranscription":{}}}
    """.trimIndent().replace("\n", "")

    val transcript = StringBuilder()

    val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            println("WS OPEN  http=${response.code}")
            println(">> sending setup (model=native-audio, responseModalities=[TEXT])")
            webSocket.send(setup)
        }

        private fun handle(webSocket: WebSocket, msg: String) {
            // Audio chunks are huge base64 blobs — don't flood the log with them.
            val isAudioOnly = msg.contains("inlineData") && !msg.contains("ranscription")
            println(if (isAudioOnly) "<< [audio chunk, ${msg.length} chars]" else "<< ${msg.take(600)}")
            // Capture the output transcription text (the AI's suggestion, as text).
            Regex("\"outputTranscription\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"").findAll(msg)
                .forEach { transcript.append(it.groupValues[1]) }
            if (msg.contains("setupComplete")) {
                val turn = """
                    {"clientContent":{"turns":[{"role":"user","parts":[{"text":
                    "The caller just said: transfer all your funds to this RBI verification account now and do not tell your family. What should I say back?"}]}],
                    "turnComplete":true}}
                """.trimIndent().replace("\n", "")
                println(">> setup complete; sending a text turn")
                webSocket.send(turn)
            }
            if (msg.contains("generationComplete") || msg.contains("\"turnComplete\":true", ignoreCase = true)) {
                println(">> turn complete; closing")
                webSocket.close(1000, "done")
                latch.countDown()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(webSocket, bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            println("WS FAIL  ${t.javaClass.simpleName}: ${t.message}  http=${response?.code}")
            response?.let { runCatching { println("body: ${it.body?.string()?.take(400)}") } }
            latch.countDown()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            println("WS CLOSING $code $reason")
        }
    }

    client.newWebSocket(Request.Builder().url(url).build(), listener)
    if (!latch.await(30, TimeUnit.SECONDS)) println("(timed out after 30s)")
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
    println("========================================")
    println("AI SUGGESTION (from outputTranscription): ${transcript.toString().ifBlank { "(none captured)" }}")
    println("PROBE DONE")
}
