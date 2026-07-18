package ai.vaarta.core.reasoning

/** Whether a live-call speech segment is confidently the app's own user, or unverified (treated
 *  exactly like today's caller-by-default behavior — spec §6.3/§7 invariant 7). There is no CALLER
 *  label here on purpose: this type answers one question only ("exclude this from scoring, or
 *  not?"), never re-implements attribution the rest of the pipeline already owns. */
enum class SpeakerLabel { USER, UNVERIFIED }

/**
 * The fail-safe speaker-attribution decision rule (redesign spec §6.3, Part D). A pure function so
 * it is unit-testable without sherpa-onnx or an emulator — [ai.vaarta.core.voice.SpeakerEmbedder]/
 * [ai.vaarta.core.voice.VoiceGallery] (core:voice) do the actual embedding + similarity check; the
 * caller passes in only the two facts this rule needs.
 *
 * Segments under 1.5s are unreliable for embeddings (spec §6.3) and are unconditionally UNVERIFIED
 * regardless of [verifiedByVoiceprint] — this is the fail-safe property under test: USER can only
 * ever be produced by BOTH a long-enough segment AND a positive voiceprint match, never either alone.
 */
object SpeakerAttributor {
    private const val MIN_SEGMENT_MS = 1_500L

    fun attribute(durationMs: Long, verifiedByVoiceprint: Boolean): SpeakerLabel =
        if (durationMs >= MIN_SEGMENT_MS && verifiedByVoiceprint) SpeakerLabel.USER else SpeakerLabel.UNVERIFIED
}
