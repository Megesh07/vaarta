package ai.vaarta.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.concurrent.thread

/**
 * Captures microphone audio as 16 kHz mono PCM16 and hands raw chunks to [onPcm] on a dedicated
 * thread (AUDIO_PIPELINE.md §2). Requires RECORD_AUDIO to already be granted. Prefers the
 * VOICE_RECOGNITION source (OEM-tuned for speech), falling back to MIC if that fails to init.
 *
 * Nothing is written to disk — PCM lives only in the transient buffer and is streamed onward
 * (privacy property P1). This is the "mic while on speakerphone" path — the only compliant way to
 * hear the caller (TECHNICAL_ARCHITECTURE.md §2).
 */
class AudioCapture(private val onPcm: (ByteArray, Int) -> Unit) {

    @Volatile private var running = false
    private var record: AudioRecord? = null

    /** Starts capture. Returns false if the mic could not be initialized (caller falls back). */
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf, CHUNK_BYTES * 4)
        val rec = createRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufSize)
            ?: createRecord(MediaRecorder.AudioSource.MIC, bufSize)
            ?: return false
        record = rec
        return try {
            rec.startRecording()
            running = true
            thread(name = "vaarta-audio", isDaemon = true) {
                val buf = ByteArray(CHUNK_BYTES)
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) onPcm(buf, n)
                }
            }
            true
        } catch (e: Exception) {
            Log.w("AudioCapture", "startRecording failed: ${e.message}")
            stop()
            false
        }
    }

    fun stop() {
        running = false
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    private fun createRecord(source: Int, bufSize: Int): AudioRecord? = try {
        @Suppress("MissingPermission") // RECORD_AUDIO is ensured granted by the caller (Activity).
        val r = AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_BYTES = 3_200 // ~100 ms at 16 kHz mono PCM16
    }
}
