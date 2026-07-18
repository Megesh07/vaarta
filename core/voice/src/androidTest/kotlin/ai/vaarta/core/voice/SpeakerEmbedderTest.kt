package ai.vaarta.core.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/** Runs on-device (needs the real .so via the emulator) — same-speaker similarity must exceed
 *  cross-speaker similarity, proving the vendored model actually discriminates voices (spec §8). */
@RunWith(AndroidJUnit4::class)
class SpeakerEmbedderTest {

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** 1 second of a fixed-frequency tone at 16 kHz mono PCM16 — a deterministic stand-in "voice A"
     *  for the smoke test (real speech fixtures are added once a physical enrollment/verify pass is
     *  scripted; this test only proves the embedder pipeline runs end-to-end and is self-consistent,
     *  not perceptual accuracy). */
    private fun tone(freqHz: Int, sampleRate: Int = 16_000, seconds: Double = 1.0): ByteArray {
        val n = (sampleRate * seconds).toInt()
        val bytes = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = (Short.MAX_VALUE * 0.5 * kotlin.math.sin(2.0 * Math.PI * freqHz * i / sampleRate)).toInt().toShort()
            bytes[i * 2] = (v.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun embedderInitializesAndProducesConsistentEmbeddings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val embedder = SpeakerEmbedder(context)
        assertTrue("SpeakerEmbedder must initialize with the vendored model asset", embedder.isReady())

        val voiceASample1 = tone(180)
        val voiceASample2 = tone(185) // close variant of the same synthetic "voice"
        val voiceB = tone(320) // clearly different synthetic "voice"

        val e1 = embedder.embed(voiceASample1, voiceASample1.size)
        val e2 = embedder.embed(voiceASample2, voiceASample2.size)
        val eB = embedder.embed(voiceB, voiceB.size)
        assertTrue("embedding 1 must not be null", e1 != null)
        assertTrue("embedding 2 must not be null", e2 != null)
        assertTrue("embedding B must not be null", eB != null)

        val sameVoiceSim = cosine(e1!!, e2!!)
        val crossVoiceSim = cosine(e1, eB!!)
        assertTrue(
            "same-source similarity ($sameVoiceSim) must exceed cross-source similarity ($crossVoiceSim)",
            sameVoiceSim > crossVoiceSim,
        )
    }
}
