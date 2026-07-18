package ai.vaarta.core.voice

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager

/**
 * Thin wrapper over sherpa-onnx's [SpeakerEmbeddingManager], scoped to exactly one enrolled name
 * ("user" — redesign spec §6.2: no multi-voice enrollment, YAGNI). Rebuilt fresh each session from
 * whatever embeddings [VoiceprintStore] (core:data) has persisted — this class holds no disk state
 * of its own, so it never needs its own encryption story.
 */
class VoiceGallery(dim: Int) {

    companion object {
        private const val NAME = "user"
    }

    private val manager = SpeakerEmbeddingManager(dim)

    /** Replace the current enrollment with [embeddings] (typically all samples loaded from disk).
     *  sherpa-onnx's [SpeakerEmbeddingManager.add] is "insert if absent" — it returns false and does
     *  NOT overwrite an existing name — so a prior enrollment must be explicitly removed first, or
     *  every call after the first one here would be a silent no-op and the enrolled voiceprint would
     *  never actually grow across a session. */
    fun enroll(embeddings: List<FloatArray>) {
        if (embeddings.isEmpty()) return
        manager.remove(NAME) // no-op (returns false) if nothing was enrolled yet — fine either way
        manager.add(NAME, embeddings.toTypedArray())
    }

    /** True if [embedding] matches the enrolled voice at or above [threshold]. False (never throws)
     *  if nothing is enrolled yet — spec §6.2's activation gate is enforced by the caller, but this
     *  is a safe default even if it weren't. */
    fun verify(embedding: FloatArray, threshold: Float): Boolean =
        manager.contains(NAME) && manager.verify(NAME, embedding, threshold)
}
