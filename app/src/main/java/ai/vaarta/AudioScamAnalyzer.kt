package ai.vaarta

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Stage
import ai.vaarta.core.reasoning.AudioAnalysis
import ai.vaarta.core.reasoning.GroundedAssessment
import ai.vaarta.core.reasoning.HybridAlert
import ai.vaarta.core.reasoning.PackLoader
import ai.vaarta.core.reasoning.RiskEngine
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.RiskState
import ai.vaarta.core.reasoning.Source
import ai.vaarta.core.reasoning.Speaker

/**
 * The recorded-call scam analyzer (ADR-0003 Phase 4D) — the after-the-fact counterpart to the live
 * [CopilotSession]. Given an audio clip, it produces the same verdict shape the live pipeline does, so
 * a recording replays through the identical [ChatThread] + [StatusBanner] UI and saves as an ordinary
 * history session (source = RECORDING).
 *
 * Score ownership is UNCHANGED from live (ADR-0002 D1): the AI transcribes and gives an advisory
 * concern, but the authoritative risk score comes from replaying the transcribed CALLER turns through
 * the deterministic [RiskEngine]. [HybridAlert] then lets the AI's concern RAISE the displayed alert
 * (catching a novel scam the pack missed) but never LOWER the deterministic floor, and reassurance
 * still needs cited consensus. No Android Context here by design — reading the file / persisting is the
 * ViewModel's job ([ai.vaarta.recording.AudioAnalyzerViewModel]).
 */
class AudioScamAnalyzer {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val langs = listOf("en", "hi_latn", "hi")

    /** The finished verdict for one recording — everything the Analyze screen and history save need. */
    data class Result(
        val chat: List<ChatItem>,
        val score: Int,
        val level: RiskLevel,
        val aiRaised: Boolean,
        val scamType: String?,
        val sources: List<Source>,
        val reassure: Boolean,
        val summary: String,
        val language: String?,
    )

    /**
     * Full pipeline for one clip. Blocking — call off the main thread. Two network calls, both
     * fail-closed independently (ADR-0002 D5): the audio analysis (required — null aborts the whole
     * thing) and the web-grounded classification (optional enhancement — null just means "no cited
     * scam-variant this time"). Returns null only if the audio analysis itself fails.
     */
    fun analyze(bytes: ByteArray, mimeType: String): Result? {
        val analysis = GeminiClient.analyzeAudio(bytes, mimeType) ?: return null
        // Reuse the exact live grounding path (Call A) for the cited scam-variant label.
        val callerContext = analysis.turns
            .filter { it.speaker != Speaker.USER }
            .takeLast(4)
            .joinToString("\n") { it.text }
        val grounded = if (callerContext.isNotBlank()) GeminiClient.classify(callerContext) else null
        return assemble(analysis, grounded)
    }

    /**
     * Pure transcript → verdict assembly, deliberately split out from the network so it is fully
     * unit-testable (no device, no HTTP). Replays the transcript through a fresh deterministic engine,
     * then combines with the AI's advisory concern and the (optional) grounded assessment via the same
     * [HybridAlert] rules the live copilot uses.
     */
    fun assemble(analysis: AudioAnalysis, grounded: GroundedAssessment?): Result {
        val engine = RiskEngine(pack, langs)
        var last = IDLE
        val chat = ArrayList<ChatItem>(analysis.turns.size + 1)

        analysis.turns.forEachIndexed { i, turn ->
            // Synthetic monotonic timestamps (a clip carries no real ones) — spaced enough that the
            // engine's decay/refractory windows behave as they would over a real call, same idea as
            // CopilotSession.runDemoCall's scripted offsets.
            val atMs = i * TURN_SPACING_MS
            if (turn.speaker == Speaker.USER) {
                chat += ChatItem.You(turn.text)
            } else {
                // CALLER or UNKNOWN → feed the deterministic engine. Mis-attributing a user line as
                // caller can only ADD scam signals (never remove protection); dropping a real caller
                // line is the dangerous error, so ambiguity resolves toward scoring (ADR-0003).
                last = engine.ingest(RiskEvent.Transcript(turn.text, atMs, atMs + 3_000, isFinal = true, confidence = 0.9f))
                chat += ChatItem.Caller(turn.text)
            }
        }

        // AI concern is advisory; combine the analyzer's own read with the grounded call's, then let
        // HybridAlert ratchet the deterministic floor UP only (never down).
        val aiConcern = maxLevel(analysis.concern, grounded?.concern ?: RiskLevel.OBSERVING)
        val level = HybridAlert.displayedLevel(last.level, aiConcern)
        val aiRaised = HybridAlert.aiRaisedAlarm(last.level, aiConcern)
        val showType = grounded != null && HybridAlert.mayShowScamType(grounded)
        // Reassurance requires cited consensus AND the analyzer's own benign read (fails toward caution).
        val reassure = grounded != null && analysis.benign && HybridAlert.mayReassure(last.level, grounded)
        val scamType = if (showType) grounded!!.scamType else null
        val sources = if (showType) grounded!!.sources else emptyList()

        // The verdict summary + cited scam-ID ride along as a final Coach entry so the whole thread
        // persists and replays through the shared ChatThread (a recording has no live replies to give).
        if (analysis.summary.isNotBlank() || scamType != null) {
            chat += ChatItem.Coach(warning = analysis.summary, replies = emptyList(), scamType = scamType, sources = sources)
        }

        return Result(
            chat = chat,
            score = last.score,
            level = level,
            aiRaised = aiRaised,
            scamType = scamType,
            sources = sources,
            reassure = reassure,
            summary = analysis.summary,
            language = analysis.language,
        )
    }

    private fun maxLevel(a: RiskLevel, b: RiskLevel): RiskLevel = if (a.ordinal >= b.ordinal) a else b

    private companion object {
        private const val TURN_SPACING_MS = 8_000L
        private val IDLE = RiskState(0, RiskLevel.OBSERVING, Stage.NONE, emptyList())
    }
}
