package ai.vaarta

import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.common.Stage
import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.ComplaintRenderers
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.ai.GeminiClient
import ai.vaarta.ai.GeminiLiveClient
import ai.vaarta.audio.AudioCapture
import ai.vaarta.core.reasoning.ConversationTurn
import ai.vaarta.core.reasoning.GroundedAssessment
import ai.vaarta.core.reasoning.HybridAlert
import ai.vaarta.core.reasoning.OwnWordsGate
import ai.vaarta.core.reasoning.PackLoader
import ai.vaarta.core.reasoning.QuestionSelector
import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskEngine
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.RiskState
import ai.vaarta.core.reasoning.Source
import ai.vaarta.core.reasoning.Speaker
import ai.vaarta.core.reasoning.SuggestionSafetyFilter
import ai.vaarta.core.reasoning.nextStage
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The live conversation copilot pipeline (ADR-0003), extracted from [SessionViewModel] into a plain,
 * lifecycle-independent holder (Phase 4C) so the SAME pipeline can run either behind the in-app UI
 * (owned by a ViewModel, scoped to `viewModelScope`) or inside the floating-overlay foreground
 * service (owned by the service, scoped to its own [CoroutineScope]). It has no Android Context
 * dependency by design — [GeminiClient], [GeminiLiveClient], and [AudioCapture] are all Context-free.
 *
 * Bridges the tested Tier-0 engine (core:reasoning) and complaint builder (core:complaint) to
 * observers via [StateFlow]s. One instance == one call session; state lives in RAM only
 * (DATABASE_DESIGN.md §2) unless a consumer explicitly persists it (core:data, ADR-0004).
 *
 * Coalesces the live client's caller-speech fragments, defends against the self-echo feedback loop,
 * ingests each turn into the deterministic engine (score ownership unchanged), and — opt-in only —
 * asks the cascaded text LLM for a warning + graded replies, safety-filtered before reaching [chat].
 *
 * @param scope the coroutine scope that owns all async work; cancelling it (via the owner's
 *   lifecycle) tears down every in-flight request. Call [close] to stop the mic/socket cleanly.
 */
class CopilotSession(private val scope: CoroutineScope) {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val langs = listOf("en", "hi_latn", "hi")
    private var engine = RiskEngine(pack, langs)
    private val questionSelector = QuestionSelector(pack)
    private var questionIndex = 0

    // Real-elapsed-time anchor for every event source (live transcript turns, manual cue taps) so
    // RiskEvent.atMs is always a genuine millisecond offset from session start (ADR-0003 bug fix —
    // the old live path advanced a synthetic +=3_000-per-fragment counter that froze the engine's
    // decay/hysteresis, which are both real-millisecond windows). SystemClock.elapsedRealtime() is
    // monotonic and immune to wall-clock adjustments, unlike System.currentTimeMillis().
    private var sessionStartRealtimeMs = SystemClock.elapsedRealtime()
    private var lastEventAtMs = 0L
    private fun nowOffsetMs(): Long = SystemClock.elapsedRealtime() - sessionStartRealtimeMs

    private val idle = RiskState(0, RiskLevel.OBSERVING, Stage.NONE, emptyList())

    private val _state = MutableStateFlow(idle)
    val state: StateFlow<RiskState> = _state.asStateFlow()

    private val _complaint = MutableStateFlow<String?>(null)
    val complaint: StateFlow<String?> = _complaint.asStateFlow()

    /** The structured draft behind [complaint]'s text — needed for PDF export, which this holder
     * doesn't render itself (no Android Context here by design). */
    private val _complaintDraft = MutableStateFlow<ComplaintDraft?>(null)
    val complaintDraft: StateFlow<ComplaintDraft?> = _complaintDraft.asStateFlow()

    /**
     * The one verification question currently shown (MOBILE_UX_SPEC.md §3.2) — null when no stage
     * has been reached yet (OBSERVING). Resolved text only; the app never needs the raw Question.
     */
    private val _currentQuestion = MutableStateFlow<String?>(null)
    val currentQuestion: StateFlow<String?> = _currentQuestion.asStateFlow()

    // --- Live AI layer (ADR-0002/0003) — opt-in, fails closed, never replaces the deterministic path ---

    /** Opt-in consent for cloud AI suggestions. OFF by default; app works fully without it. */
    private val _aiEnabled = MutableStateFlow(false)
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    /** Safe single-shot AI suggestion text (demo/text-mode path only), or null. */
    private val _aiSuggestion = MutableStateFlow<String?>(null)
    val aiSuggestion: StateFlow<String?> = _aiSuggestion.asStateFlow()

    /** True only if a key is compiled in — lets the UI explain when AI can't be enabled. */
    val aiConfigured: Boolean = GeminiClient.isConfigured()

    /** The most recent thing the "caller" said (from transcript events) — the single-shot AI's input. */
    private var lastCallerLine: String = ""

    // --- Conversation copilot thread (ADR-0003 / Phase 4A) — the WhatsApp-style chat ---

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat: StateFlow<List<ChatItem>> = _chat.asStateFlow()

    private fun appendChat(item: ChatItem) {
        _chat.value = (_chat.value + item).takeLast(MAX_CHAT_ITEMS)
    }

    // --- Hybrid safety-ratchet (ADR-0003): deterministic floor + web-grounded AI concern ---

    /** Latest web-grounded assessment (session-cached). Null until the first grounded turn. */
    private val _grounded = MutableStateFlow<GroundedAssessment?>(null)

    /** The alert level the banner shows = max(deterministic level, AI concern). The AI can raise it
     *  (catching novel scams the pack missed) but never lower it below the deterministic floor. */
    private val _displayedLevel = MutableStateFlow(RiskLevel.OBSERVING)
    val displayedLevel: StateFlow<RiskLevel> = _displayedLevel.asStateFlow()

    /** True only when the AI raised the alert above the deterministic floor this session (banner
     *  labels it "identified from the live web"). */
    private val _aiRaised = MutableStateFlow(false)
    val aiRaised: StateFlow<Boolean> = _aiRaised.asStateFlow()

    /** The current web-identified scam variant, shown only when source-backed (else null). */
    private val _scamType = MutableStateFlow<String?>(null)
    val scamType: StateFlow<String?> = _scamType.asStateFlow()

    private val _scamSources = MutableStateFlow<List<Source>>(emptyList())
    val scamSources: StateFlow<List<Source>> = _scamSources.asStateFlow()

    /** True only on cited consensus that the call is benign (HybridAlert.mayReassure). */
    private val _reassure = MutableStateFlow(false)
    val reassure: StateFlow<Boolean> = _reassure.asStateFlow()

    // Selective grounding: cap per session, and only re-ground when the stage advances (quota + $0).
    private var lastGroundedStage: Stage? = null
    private var groundingCount = 0
    private val MAX_GROUNDING_PER_SESSION = 12

    /** Best-effort defense against the self-echo feedback loop: a user reading a suggested reply
     *  aloud must not be scored (or coached on) as if the CALLER said it (ADR-0003 problem 1). */
    private val ownWordsGate = OwnWordsGate()

    /** Bounded rolling transcript sent to the coach model — real caller lines + the replies we
     *  displayed (so it has continuity even without proof the user spoke them verbatim). RAM-only,
     *  discarded on reset/session end like everything else here (privacy P2). */
    private val conversationHistory = ArrayDeque<ConversationTurn>()
    private val MAX_HISTORY_TURNS = 20
    private val MAX_CHAT_ITEMS = 150

    // Live fragments arrive per-delta, not per-turn (GeminiLiveClient has no input "turn complete"
    // signal) — coalesce by flushing after a short quiet period, same idea as debounced typing.
    private val inputBuffer = StringBuilder()
    private var turnFlushJob: Job? = null
    private val TURN_SILENCE_MS = 1_200L

    // Free-tier rate limit guard (~10-15 RPM) — skip coaching a turn rather than risk exhausting
    // quota; the deterministic score/question never depends on this succeeding (D5, fails closed).
    private var lastCoachRequestRealtimeMs = 0L
    private val MIN_COACH_INTERVAL_MS = 4_000L

    // --- Live audio listening (ADR-0002/0003): mic -> Gemini Live (transcription only) -> engine + copilot ---
    private var liveClient: GeminiLiveClient? = null
    private var audioCapture: AudioCapture? = null

    /** True while the user intends to be listening — drives auto-reconnect on transient errors,
     *  distinct from whether a socket happens to be connected right now. */
    private var userWantsLiveListening = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3

    /** Incremented on every start/stop so a stale session's late-arriving callbacks (fired from the
     *  OkHttp listener thread just before teardown) are dropped instead of mutating a dead session's
     *  now-reused state (ADR-0003 bug fix). */
    private var listeningGeneration = 0

    /** null = not listening; otherwise the live-session status label for the UI. */
    private val _liveStatus = MutableStateFlow<String?>(null)
    val liveStatus: StateFlow<String?> = _liveStatus.asStateFlow()

    /** Start live listening. Caller (Activity/Service) MUST have RECORD_AUDIO granted first. */
    fun startLiveListening() {
        userWantsLiveListening = true
        reconnectAttempts = 0
        startLiveListeningInternal()
    }

    private fun startLiveListeningInternal() {
        if (liveClient != null) return
        _aiEnabled.value = true // live listening implies the AI layer is on
        val myGeneration = ++listeningGeneration
        val client = GeminiLiveClient(
            onCallerText = { text -> scope.launch { if (myGeneration == listeningGeneration) onLiveCallerFragment(text) } },
            // The native-audio model's own suggestion is no longer consumed for coaching (ADR-0003
            // demotes the live client to transcription-only — it cannot emit structured JSON; the
            // cascaded coach() call below is the real intelligence). Left wired but ignored, rather
            // than touching GeminiLiveClient itself, per the plan's lowest-risk choice.
            onSuggestion = { },
            onStatus = { st -> if (myGeneration == listeningGeneration) scope.launch { onLiveStatus(st) } },
        )
        liveClient = client
        client.start()
        val capture = AudioCapture { pcm, len -> client.sendAudio(pcm, len) }
        if (!capture.start()) {
            onLiveStatus(GeminiLiveClient.Status.ERROR)
            return
        }
        audioCapture = capture
    }

    private fun onLiveStatus(status: GeminiLiveClient.Status) {
        _liveStatus.value = status.name
        when (status) {
            GeminiLiveClient.Status.LISTENING -> reconnectAttempts = 0
            GeminiLiveClient.Status.ERROR, GeminiLiveClient.Status.CLOSED -> {
                // Always fully clean up so a retry (manual or automatic) is never wedged — the old
                // bug left `liveClient` non-null after an ERROR, so startLiveListening()'s
                // `if (liveClient != null) return` guard silently blocked every future restart.
                audioCapture?.stop(); audioCapture = null
                liveClient = null
                if (userWantsLiveListening && status == GeminiLiveClient.Status.ERROR && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    val attempt = reconnectAttempts
                    scope.launch {
                        delay(2_000L * attempt) // simple backoff — covers transient drops and the ~15 min free-tier session cap
                        if (userWantsLiveListening) startLiveListeningInternal()
                    }
                }
            }
            GeminiLiveClient.Status.CONNECTING -> Unit
        }
    }

    fun stopLiveListening() {
        userWantsLiveListening = false
        listeningGeneration++ // orphans any in-flight callback from the session being torn down
        audioCapture?.stop(); audioCapture = null
        liveClient?.close(); liveClient = null
        _liveStatus.value = null
        _aiLoading.value = false
        turnFlushJob?.cancel()
        inputBuffer.clear()
    }

    /** Caller-speech fragment arrives → buffer and (re)schedule a flush after a quiet period, since
     *  the live client streams input as deltas with no per-turn boundary of its own. */
    private fun onLiveCallerFragment(fragment: String) {
        if (fragment.isBlank()) return
        inputBuffer.append(fragment)
        turnFlushJob?.cancel()
        turnFlushJob = scope.launch {
            delay(TURN_SILENCE_MS)
            val text = inputBuffer.toString().trim()
            inputBuffer.clear()
            if (text.isNotEmpty()) processCallerTurn(text)
        }
    }

    /** One coalesced caller turn → engine (score, unchanged owner) → conversation history → coach. */
    private fun processCallerTurn(text: String) {
        // Primary defense against the self-echo loop (ADR-0003 problem 1): if this is close enough
        // to something VAARTA itself just displayed, it's almost certainly the USER reading it back
        // on speakerphone, not the caller's independent speech — attribute it to the user (show it as
        // a "you said" bubble for continuity), and do NOT score or coach on it.
        if (ownWordsGate.isLikelyOwnWords(text)) {
            appendChat(ChatItem.You(text))
            conversationHistory.addLast(ConversationTurn(Speaker.USER, text, nowOffsetMs()))
            while (conversationHistory.size > MAX_HISTORY_TURNS) conversationHistory.removeFirst()
            return
        }

        val atMs = nowOffsetMs()
        lastEventAtMs = atMs
        lastCallerLine = text
        appendChat(ChatItem.Caller(text))
        val newState = engine.ingest(RiskEvent.Transcript(text, atMs, atMs + 3_000, isFinal = true, confidence = 0.9f))
        applyState(newState)

        conversationHistory.addLast(ConversationTurn(Speaker.CALLER, text, atMs))
        while (conversationHistory.size > MAX_HISTORY_TURNS) conversationHistory.removeFirst()

        requestIntelligence(callerLine = text, state = newState)
    }

    /**
     * The hybrid intelligence step (ADR-0003) for one caller turn. Runs two Gemini calls that both
     * FAIL CLOSED independently — the deterministic score/question already rendered above never
     * depends on either succeeding (ADR-0002 D5):
     *  - Call B (always, if AI on): the structured coach → warning + graded replies.
     *  - Call A (selective): web-grounded classification → scam variant + concern + cited sources.
     *    Only when the stage advanced and under a per-session cap (quota + $0).
     * Neither call can lower the alert: [HybridAlert] combines the AI concern with the deterministic
     * floor by max(), and any scam-variant/benign claim needs a cited source.
     */
    private fun requestIntelligence(callerLine: String, state: RiskState) {
        if (!_aiEnabled.value || !aiConfigured) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCoachRequestRealtimeMs < MIN_COACH_INTERVAL_MS) return
        lastCoachRequestRealtimeMs = now

        val historySnapshot = conversationHistory.toList()
        val stage = state.stage.name
        val next = nextStage(state.stage).name

        // Ground selectively: only once per stage, capped per session; caller lines only (privacy).
        val shouldGround = state.stage != Stage.NONE &&
            groundingCount < MAX_GROUNDING_PER_SESSION &&
            (_grounded.value == null || state.stage != lastGroundedStage)
        val callerContext = historySnapshot
            .filter { it.speaker == Speaker.CALLER }
            .takeLast(4)
            .joinToString("\n") { it.text }

        // Only forward a scam type that already passed the same source-backed gate the UI banner
        // uses (HybridAlert.mayShowScamType) — never an uncited claim, mirroring the display rule.
        val g0 = _grounded.value
        val groundedScamTypeForCoach = if (g0 != null && HybridAlert.mayShowScamType(g0)) g0.scamType else null

        scope.launch {
            val coachDeferred = async(Dispatchers.IO) { GeminiClient.coach(historySnapshot, stage, next, groundedScamTypeForCoach) }
            val groundDeferred =
                if (shouldGround) async(Dispatchers.IO) { GeminiClient.classify(callerContext) } else null

            val grounded = groundDeferred?.await()
            if (grounded != null) {
                groundingCount++
                lastGroundedStage = state.stage
                _grounded.value = grounded
                recomputeDisplayed(_state.value)
            }

            val safe = coachDeferred.await()?.let { SuggestionSafetyFilter.sanitize(it) } ?: return@launch
            // Remember what we're showing so a future echo of THESE exact words is recognized as the
            // user reading them back (self-echo defense); give the coach continuity by appending the
            // primary reply as the USER turn — planning context only, never affects the score.
            safe.replies.forEach { ownWordsGate.remember(it.text) }
            conversationHistory.addLast(ConversationTurn(Speaker.USER, safe.replies.first().text, nowOffsetMs()))
            while (conversationHistory.size > MAX_HISTORY_TURNS) conversationHistory.removeFirst()

            val g = _grounded.value
            val showType = g != null && HybridAlert.mayShowScamType(g)
            appendChat(
                ChatItem.Coach(
                    warning = safe.warning,
                    replies = safe.replies,
                    scamType = if (showType) g!!.scamType else null,
                    sources = if (showType) g!!.sources else emptyList(),
                ),
            )
        }
    }

    /** Recompute the hybrid banner from the current deterministic state + latest grounded assessment. */
    private fun recomputeDisplayed(state: RiskState) {
        val g = _grounded.value
        val aiConcern = g?.concern ?: RiskLevel.OBSERVING
        _displayedLevel.value = HybridAlert.displayedLevel(state.level, aiConcern)
        _aiRaised.value = HybridAlert.aiRaisedAlarm(state.level, aiConcern)
        val showType = g != null && HybridAlert.mayShowScamType(g)
        _scamType.value = if (showType) g!!.scamType else null
        _scamSources.value = if (showType) g!!.sources else emptyList()
        _reassure.value = g != null && HybridAlert.mayReassure(state.level, g)
    }

    /** Stop the mic/socket cleanly. Call from the owner's teardown (ViewModel.onCleared / Service
     *  onDestroy). Does NOT cancel [scope] — the owner owns that lifecycle. */
    fun close() {
        stopLiveListening()
    }

    fun reset() {
        // A demo replay or a fresh session can't coexist with an active live call on the same
        // engine/clock without interleaving fragments into a swapped-out session (ADR-0003 bug fix)
        // — stop it first. The user can tap Start again if they want to keep listening.
        stopLiveListening()
        engine = RiskEngine(pack, langs)
        sessionStartRealtimeMs = SystemClock.elapsedRealtime()
        lastEventAtMs = 0
        questionIndex = 0
        applyState(idle)
        _complaint.value = null
        _complaintDraft.value = null
        lastCallerLine = ""
        _aiSuggestion.value = null
        _aiLoading.value = false
        _chat.value = emptyList()
        conversationHistory.clear()
        ownWordsGate.reset()
        _grounded.value = null
        _displayedLevel.value = RiskLevel.OBSERVING
        _aiRaised.value = false
        _scamType.value = null
        _scamSources.value = emptyList()
        _reassure.value = false
        lastGroundedStage = null
        groundingCount = 0
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
     * Ask the AI for a single-shot suggested reply to [lastCallerLine] (demo/text-mode path,
     * ADR-0002 — kept alongside the live copilot in [chat]). Async, off the main thread, and FAILS
     * CLOSED: not enabled / not configured / no caller line / any error / unsafe output all leave
     * [aiSuggestion] null so the UI shows only the deterministic question.
     */
    private fun requestAiSuggestion() {
        if (!_aiEnabled.value || !aiConfigured || lastCallerLine.isBlank()) {
            _aiSuggestion.value = null
            return
        }
        val line = lastCallerLine
        val stage = _state.value.stage.name
        _aiLoading.value = true
        scope.launch {
            val suggestion = withContext(Dispatchers.IO) { GeminiClient.suggest(line, stage) }
            // Rail: only show it if it survives the safety filter; else stay null (deterministic).
            _aiSuggestion.value = suggestion?.let { SuggestionSafetyFilter.sanitizedOrNull(it) }
            _aiLoading.value = false
        }
    }

    /**
     * Rig-mode demo: play a scripted digital-arrest call through the real engine and build the chat
     * thread — each caller line becomes a Caller bubble + a Coach bubble using the turn's own
     * deterministic explanation + verification question (zero network cost, works offline), so the
     * WhatsApp-style UI is exercised even without a key. If AI is opted in, a real single-shot
     * suggestion is still requested for the final line as before.
     */
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
        val items = mutableListOf<ChatItem>()
        for ((t, text) in script) {
            last = engine.ingest(RiskEvent.Transcript(text, t, t + 3_000, isFinal = true, confidence = 0.9f))
            items += ChatItem.Caller(text)
            val explain = last.topSignals.firstOrNull()?.explain
            val question = questionSelector.select(last.stage, 0)?.let { questionSelector.textFor(it, "en") }
            if (explain != null && question != null) {
                items += ChatItem.Coach(explain, listOf(Reply(question, ReplyKind.VERIFY)))
            }
        }
        _chat.value = items
        lastEventAtMs = 155_000
        lastCallerLine = script.last().second // the single-shot AI reacts to the latest caller utterance
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
        // Keep the hybrid banner in step with the deterministic floor even between groundings.
        recomputeDisplayed(newState)
    }

    fun generateComplaint() {
        val fired = engine.sessionSignals()
        if (fired.isEmpty()) {
            _complaint.value = "No warning signs detected yet — run the demo call or start live protection first."
            _complaintDraft.value = null
            return
        }
        val start = 1_720_000_000_000L
        val input = ComplaintInput(
            callerNumber = "+91 92XXXXXX21",
            callStartEpochMs = start,
            callEndEpochMs = start + lastEventAtMs + 5_000,
            languages = listOf("en", "hi"),
            matchedScamCode = "SC-01",
            matchedScamName = "Digital Arrest - police/CBI impersonation",
            finalScore = _state.value.score,
            detectedSignals = fired.map { DetectedSignal(it.signalId, it.category, it.stage, it.atMs, it.explain) },
        )
        val draft = ComplaintBuilder.assemble(input, generatedAtEpochMs = start + lastEventAtMs + 20_000)
        _complaintDraft.value = draft
        _complaint.value = ComplaintRenderers.toText(draft)
    }
}
