package ai.vaarta.core.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/**
 * On-device speaker-embedding extraction (redesign spec §6.2/§6.6, Part D). Wraps sherpa-onnx's
 * [SpeakerEmbeddingExtractor]; the model asset ships in this module ([ASSET_MODEL_PATH]). Fails
 * closed: any init/inference error returns null rather than throwing, so a caller can always treat
 * "no embedding" as "leave this segment unverified" (spec §7 error handling) — the same fail-safe
 * posture as every other AI-adjacent component in this app.
 */
class SpeakerEmbedder(context: Context) {

    companion object {
        private const val ASSET_MODEL_PATH = "speaker_embedding.onnx"
        private const val SAMPLE_RATE = 16_000
    }

    private val extractor: SpeakerEmbeddingExtractor? = try {
        SpeakerEmbeddingExtractor(
            assetManager = context.assets,
            config = SpeakerEmbeddingExtractorConfig(model = ASSET_MODEL_PATH, numThreads = 1, provider = "cpu"),
        )
    } catch (e: Exception) {
        Log.w("SpeakerEmbedder", "init failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    fun dim(): Int = extractor?.dim() ?: 0

    /** True only if the extractor initialized successfully — callers should treat false as
     *  "attribution permanently unverified for this session" (spec §7). */
    fun isReady(): Boolean = extractor != null

    /**
     * Computes an embedding for [lengthBytes] of 16-bit mono PCM at [sampleRate] Hz. Returns null on
     * any failure (extractor not ready, empty input, native error) — never throws.
     */
    fun embed(pcm16: ByteArray, lengthBytes: Int, sampleRate: Int = SAMPLE_RATE): FloatArray? {
        val ext = extractor ?: return null
        if (lengthBytes <= 0) return null
        return try {
            val samples = FloatArray(lengthBytes / 2)
            for (i in samples.indices) {
                val lo = pcm16[i * 2].toInt() and 0xFF
                val hi = pcm16[i * 2 + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort()
                samples[i] = sample / 32768f
            }
            val stream = ext.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                stream.inputFinished()
                if (!ext.isReady(stream)) return null
                ext.compute(stream)
            } finally {
                stream.release()
            }
        } catch (e: Exception) {
            Log.w("SpeakerEmbedder", "embed failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun close() {
        // Frees native ONNX runtime buffers deterministically rather than relying on JVM
        // finalization — verified via javap that SpeakerEmbeddingExtractor.release() is public.
        extractor?.release()
    }
}
