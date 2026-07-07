package ai.vaarta

import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Stage
import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.ComplaintRenderers
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.PackLoader
import ai.vaarta.core.reasoning.QuestionSelector
import ai.vaarta.core.reasoning.RiskEngine
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.RiskState
import ai.vaarta.core.reasoning.SuggestionSafetyFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges the tested Tier-0 engine (core:reasoning) and complaint builder (core:complaint) to the
 * Compose UI. One instance == one call session; state lives in RAM only (DATABASE_DESIGN.md §2).
 */
class SessionViewModel : ViewModel() {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val langs = listOf("en", "hi_latn", "hi")
    private var engine = RiskEngine(pack, langs)
    private val questionSelector = QuestionSelector(pack)
    private var clockMs = 0L
    private var questionIndex = 0

    private val idle = RiskState(0, RiskLevel.OBSERVING, Stage.NONE, emptyList())

    private val _state = MutableStateFlow(idle)
    val state: StateFlow<RiskState> = _state.asStateFlow()

    private val _tapped = MutableStateFlow(emptySet<String>())
    val tapped: StateFlow<Set<String>> = _tapped.asStateFlow()

    private val _complaint = MutableStateFlow<String?>(null)
    val complaint: StateFlow<String?> = _complaint.asStateFlow()

    /** The structured draft behind [complaint]'s text — needed for PDF export, which the
     * ViewModel doesn't render itself (no Android Context here by design). */
    private val _complaintDraft = MutableStateFlow<ComplaintDraft?>(null)
    val complaintDraft: StateFlow<ComplaintDraft?> = _complaintDraft.asStateFlow()

    /**
     * The one verification question currently shown (MOBILE_UX_SPEC.md §3.2) — null when no stage
     * has been reached yet (OBSERVING). Resolved text only; the app never needs the raw Question.
     */
    private val _currentQuestion = MutableStateFlow<String?>(null)
    val currentQuestion: StateFlow<String?> = _currentQuestion.asStateFlow()

    // --- Live AI layer (ADR-0002) — opt-in, fails closed, never replaces the deterministic path ---

    /** Opt-in consent for cloud AI suggestions. OFF by default; app works fully without it. */
    private val _aiEnabled = MutableStateFlow(false)
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    /** Safe AI suggestion text to show, or null (never shown if unavailable/unsafe → deterministic). */
    private val _aiSuggestion = MutableStateFlow<String?>(null)
    val aiSuggestion: StateFlow<String?> = _aiSuggestion.asStateFlow()

    /** True only if a key is compiled in — lets the UI explain when AI can't be enabled. */
    val aiConfigured: Boolean = GeminiClient.isConfigured()

    /** The most recent thing the "caller" said (from transcript events) — the AI's input. */
    private var lastCallerLine: String = ""

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
        applyState(engine.ingest(RiskEvent.ManualCue(cueId, clockMs)))
        _tapped.value = _tapped.value + cueId
        _complaint.value = null
        _complaintDraft.value = null
    }

    fun reset() {
        engine = RiskEngine(pack, langs)
        clockMs = 0
        questionIndex = 0
        applyState(idle)
        _tapped.value = emptySet()
        _complaint.value = null
        _complaintDraft.value = null
        lastCallerLine = ""
        _aiSuggestion.value = null
        _aiLoading.value = false
    }

    /** Opt-in toggle for the cloud AI layer. Turning it off immediately clears any AI suggestion. */
    fun setAiEnabled(enabled: Boolean) {
        _aiEnabled.value = enabled
        if (!enabled) {
            _aiSuggestion.value = null
            _aiLoading.value = false
        } else if (lastCallerLine.isNotBlank()) {
            requestAiSuggestion()
        }
    }

    /**
     * Ask the AI for a suggested reply to [lastCallerLine]. Async, off the main thread, and
     * FAILS CLOSED: not enabled / not configured / no caller line / any error / unsafe output all
     * leave [aiSuggestion] null so the UI shows only the deterministic question (ADR-0002).
     */
    private fun requestAiSuggestion() {
        if (!_aiEnabled.value || !aiConfigured || lastCallerLine.isBlank()) {
            _aiSuggestion.value = null
            return
        }
        val line = lastCallerLine
        val stage = _state.value.stage.name
        _aiLoading.value = true
        viewModelScope.launch {
            val suggestion = withContext(Dispatchers.IO) { GeminiClient.suggest(line, stage) }
            // Rail: only show it if it survives the safety filter; else stay null (deterministic).
            _aiSuggestion.value = suggestion?.let { SuggestionSafetyFilter.sanitizedOrNull(it) }
            _aiLoading.value = false
        }
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
        lastCallerLine = script.last().second // the AI reacts to the latest caller utterance
        applyState(last)
        requestAiSuggestion() // no-op unless AI is opted in
    }

    /** Tap = next suggestion (MOBILE_UX_SPEC.md §3.2) — cycles among questions relevant to the current stage. */
    fun cycleQuestion() {
        questionIndex++
        _currentQuestion.value = questionSelector.select(_state.value.stage, questionIndex)?.let {
            questionSelector.textFor(it, "en")
        }
    }

    private fun applyState(newState: RiskState) {
        _state.value = newState
        _currentQuestion.value = questionSelector.select(newState.stage, questionIndex)?.let {
            questionSelector.textFor(it, "en")
        }
    }

    fun generateComplaint() {
        val fired = engine.sessionSignals()
        if (fired.isEmpty()) {
            _complaint.value = "No warning signs detected yet — tap cues or run the demo call first."
            _complaintDraft.value = null
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
        val draft = ComplaintBuilder.assemble(input, generatedAtEpochMs = start + clockMs + 20_000)
        _complaintDraft.value = draft
        _complaint.value = ComplaintRenderers.toText(draft)
    }
}
