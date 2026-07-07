package ai.vaarta

import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Stage
import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.ComplaintRenderers
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.core.reasoning.PackLoader
import ai.vaarta.core.reasoning.RiskEngine
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.RiskState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the tested Tier-0 engine (core:reasoning) and complaint builder (core:complaint) to the
 * Compose UI. One instance == one call session; state lives in RAM only (DATABASE_DESIGN.md §2).
 */
class SessionViewModel : ViewModel() {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val langs = listOf("en", "hi_latn", "hi")
    private var engine = RiskEngine(pack, langs)
    private var clockMs = 0L

    private val idle = RiskState(0, RiskLevel.OBSERVING, Stage.NONE, emptyList())

    private val _state = MutableStateFlow(idle)
    val state: StateFlow<RiskState> = _state.asStateFlow()

    private val _tapped = MutableStateFlow(emptySet<String>())
    val tapped: StateFlow<Set<String>> = _tapped.asStateFlow()

    private val _complaint = MutableStateFlow<String?>(null)
    val complaint: StateFlow<String?> = _complaint.asStateFlow()

    /**
     * Manual Mode cues (id -> label) — MOBILE_UX_SPEC.md §3.3.
     * Every signal in the pack that carries a `manualCue` must have a matching entry here
     * (IMPLEMENTATION_GUARDRAILS.md ALWAYS #10 — audio-derived signals need Manual Mode parity).
     */
    val cues: List<Pair<String, String>> = listOf(
        "CUE_CLAIMS_AUTHORITY" to "Says POLICE / CBI / ED",
        "CUE_THREATENS_ARREST" to "Threatens ARREST / warrant",
        "CUE_PARCEL" to "Mentions PARCEL / customs",
        "CUE_BADGE_CASE_ID" to "Gives badge / case number unprompted",
        "CUE_ASKS_AADHAAR_OTP" to "Asks for AADHAAR / OTP / account",
        "CUE_DONT_TELL_ANYONE" to "Says DON'T TELL ANYONE",
        "CUE_STAY_ON_LINE" to "Says STAY ON THE LINE",
        "CUE_MOVE_TO_WHATSAPP" to "Move to WHATSAPP / video",
        "CUE_SENT_FAKE_DOCS" to "Sends warrant / freeze 'document'",
        "CUE_PRESSURE_STAY" to "Urgency / deadline",
        "CUE_DEMANDS_MONEY" to "Demands MONEY / UPI",
    )

    fun tapCue(cueId: String) {
        clockMs += 15_000
        _state.value = engine.ingest(RiskEvent.ManualCue(cueId, clockMs))
        _tapped.value = _tapped.value + cueId
        _complaint.value = null
    }

    fun reset() {
        engine = RiskEngine(pack, langs)
        clockMs = 0
        _state.value = idle
        _tapped.value = emptySet()
        _complaint.value = null
    }

    /** Rig-mode demo: play a scripted digital-arrest call through the real engine. */
    fun runDemoCall() {
        reset()
        val script = listOf(
            5_000L to "Hello, this is about a parcel in your name that customs has seized with illegal items",
            20_000L to "I am transferring you to the CBI crime branch cyber cell now",
            45_000L to "Officer Sharma badge number 4471, an arrest warrant and FIR are registered against you for money laundering",
            75_000L to "You are now under digital arrest, do not disconnect and do not tell anyone in your family",
            100_000L to "Install WhatsApp and join the video call, keep your camera on",
            125_000L to "A non bailable arrest warrant has been issued and your account will be frozen within two hours",
            155_000L to "Transfer the money to this RBI supervised account to verify your funds",
        )
        var last = idle
        for ((t, text) in script) {
            last = engine.ingest(RiskEvent.Transcript(text, t, t + 3_000, isFinal = true, confidence = 0.9f))
        }
        clockMs = 155_000
        _state.value = last
    }

    fun generateComplaint() {
        val fired = engine.sessionSignals()
        if (fired.isEmpty()) {
            _complaint.value = "No warning signs detected yet — tap cues or run the demo call first."
            return
        }
        val start = 1_720_000_000_000L
        val input = ComplaintInput(
            callerNumber = "+91 92XXXXXX21",
            callStartEpochMs = start,
            callEndEpochMs = start + clockMs + 5_000,
            languages = listOf("en", "hi"),
            matchedScamCode = "SC-01",
            matchedScamName = "Digital Arrest - police/CBI impersonation",
            finalScore = _state.value.score,
            detectedSignals = fired.map { DetectedSignal(it.signalId, it.category, it.stage, it.atMs, it.explain) },
        )
        _complaint.value = ComplaintRenderers.toText(
            ComplaintBuilder.assemble(input, generatedAtEpochMs = start + clockMs + 20_000),
        )
    }
}
