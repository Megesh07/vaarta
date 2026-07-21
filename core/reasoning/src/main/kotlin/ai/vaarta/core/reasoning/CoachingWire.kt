package ai.vaarta.core.reasoning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Raw wire shape of one reply from the coach model's structured-output JSON (ADR-0003). */
@Serializable
data class CoachWireReply(val text: String = "", val kind: String = "")

/** Raw wire shape of the coach model's structured-output JSON, before validation. */
@Serializable
data class CoachWireResponse(val warning: String = "", val replies: List<CoachWireReply> = emptyList())

/** Raw wire shape of the grounded classification (Call A) — JSON-in-text, no strict schema (the
 *  grounded call can't use responseSchema, ADR-0003). */
@Serializable
data class GroundedWire(val scamType: String = "", val concern: String = "", val benign: Boolean = false)

/** Raw wire shape of one diarized turn from the recorded-call analyzer (ADR-0003 Phase 4D). */
@Serializable
data class AudioTurnWire(val speaker: String = "", val text: String = "")

/** Raw wire shape of the panic-sheet personalization (redesign spec §A2), before validation. */
@Serializable
data class PanicWireResponse(val heading: String = "", val steps: List<String> = emptyList())

/** Raw wire shape of the recorded-call analysis, before validation (ADR-0003 Phase 4D). */
@Serializable
data class AudioAnalysisWire(
    val turns: List<AudioTurnWire> = emptyList(),
    val concern: String = "",
    val summary: String = "",
    val benign: Boolean = false,
    val language: String = "",
)

/** Tolerant string → [RiskLevel] mapping for the model's free-text concern label. Unknown → OBSERVING
 *  (fails toward "no extra alarm", which is safe because it can only ever fail to RAISE, never lower). */
fun riskLevelFromWire(raw: String?): RiskLevel = when (raw?.trim()?.uppercase()) {
    "SCAM_PATTERN", "SCAM", "CRITICAL", "DANGER" -> RiskLevel.SCAM_PATTERN
    "HIGH_RISK", "HIGH", "SEVERE" -> RiskLevel.HIGH_RISK
    "CAUTION", "MEDIUM", "WARN", "WARNING", "SUSPICIOUS" -> RiskLevel.CAUTION
    else -> RiskLevel.OBSERVING
}

/**
 * Pure parsing/validation from the coach model's wire JSON to the validated [CoachingResponse]
 * domain shape — deliberately separate from [ai.vaarta.ai.GeminiClient]'s network I/O so it is
 * unit-testable without a device or HTTP mocking, the same split already used for [LiveSuggestion]
 * (core:reasoning) vs. the REST transport (app). Tolerant of partial/malformed output: drops any
 * reply with blank text or an unrecognized `kind`, and fails closed (null) if the warning is blank
 * or nothing usable survives — the caller falls back to the deterministic question bank.
 */
object CoachingWireParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses the model's raw JSON text (already unwrapped from the Gemini response envelope). */
    fun parse(rawJson: String): CoachingResponse? {
        val wire = runCatching { json.decodeFromString(CoachWireResponse.serializer(), rawJson) }.getOrNull() ?: return null
        return fromWire(wire)
    }

    fun fromWire(wire: CoachWireResponse): CoachingResponse? {
        if (wire.warning.isBlank()) return null
        val replies = wire.replies.mapNotNull { r ->
            if (r.text.isBlank()) return@mapNotNull null
            val kind = ReplyKind.fromWire(r.kind) ?: return@mapNotNull null
            Reply(r.text.trim(), kind)
        }
        if (replies.isEmpty()) return null
        return CoachingResponse(wire.warning.trim(), replies)
    }

    /**
     * Parses the grounded classification (Call A): the model's text (JSON-in-text, possibly wrapped
     * in prose or markdown fences) plus the cited [sources] the caller already extracted from
     * Gemini's grounding metadata. Always returns a value — on any parse failure it returns a NEUTRAL
     * assessment (OBSERVING, no claim) so a garbled classification can only fail to raise the alarm,
     * never lower it or fabricate a benign verdict. Source-backing of scamType/benign is enforced
     * downstream by [HybridAlert], not here.
     */
    fun parseGroundedAssessment(modelText: String?, sources: List<Source>): GroundedAssessment {
        val jsonText = extractJsonObject(modelText) ?: return GroundedAssessment(sources = sources)
        val wire = runCatching { json.decodeFromString(GroundedWire.serializer(), jsonText) }.getOrNull()
            ?: return GroundedAssessment(sources = sources)
        return GroundedAssessment(
            concern = riskLevelFromWire(wire.concern),
            scamType = wire.scamType.trim().ifBlank { null },
            benign = wire.benign,
            sources = sources,
        )
    }

    /**
     * Parses the recorded-call analyzer's structured JSON (ADR-0003 Phase 4D) into the validated
     * [AudioAnalysis]. Fails CLOSED (null) if the JSON is malformed or yields no usable turns — the
     * caller then shows "couldn't analyze" rather than a fabricated verdict. [concern] maps tolerantly
     * (unknown → OBSERVING, safe because it can only RAISE the alert via [HybridAlert], never lower it);
     * blank-text turns are dropped; each speaker maps through [Speaker.fromWire] (unknown → UNKNOWN,
     * which the analyzer still scores, failing toward catching a scam).
     */
    fun parseAudioAnalysis(rawJson: String?): AudioAnalysis? {
        if (rawJson.isNullOrBlank()) return null
        val wire = runCatching { json.decodeFromString(AudioAnalysisWire.serializer(), rawJson) }.getOrNull() ?: return null
        val turns = wire.turns.mapNotNull { t ->
            if (t.text.isBlank()) return@mapNotNull null
            AudioTurn(Speaker.fromWire(t.speaker), t.text.trim())
        }
        if (turns.isEmpty()) return null
        return AudioAnalysis(
            turns = turns,
            concern = riskLevelFromWire(wire.concern),
            summary = wire.summary.trim(),
            benign = wire.benign,
            language = wire.language.trim().ifBlank { null },
        )
    }

    /**
     * Parses the panic-personalization model's structured JSON (redesign spec §A2). Fails closed
     * (null) if the heading is blank or no step survives — the panic sheet then shows only the
     * always-present base steps. Caps at 4 steps (matches the base [RightNowSteps] count) and drops
     * any blank step.
     */
    fun parsePanicPersonalization(rawJson: String?): PanicPersonalization? {
        if (rawJson.isNullOrBlank()) return null
        val wire = runCatching { json.decodeFromString(PanicWireResponse.serializer(), rawJson) }.getOrNull() ?: return null
        if (wire.heading.isBlank()) return null
        val steps = wire.steps.map { it.trim() }.filter { it.isNotBlank() }.take(4)
        if (steps.isEmpty()) return null
        return PanicPersonalization(wire.heading.trim(), steps)
    }

    /** Leniently pulls the first `{ … }` object out of a text blob (grounded output isn't strict JSON). */
    private fun extractJsonObject(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }
}
