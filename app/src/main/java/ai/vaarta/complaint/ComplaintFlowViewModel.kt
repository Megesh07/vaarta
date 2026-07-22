package ai.vaarta.complaint

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.ComplaintDestination
import ai.vaarta.core.reasoning.ComplaintPlaybookLoader
import ai.vaarta.core.reasoning.ComplaintRouter
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ComplaintStep { PREPARE, REVIEW, FILE }

data class ComplaintFlowState(
    val step: ComplaintStep = ComplaintStep.PREPARE,
    val destinations: List<ComplaintDestination> = emptyList(),
    val selected: ComplaintDestination? = null,
    val packet: ComplaintPacket? = null,
    val identity: IdentityDetails? = null,
    val loss: LossInput? = null,
    val freshnessNote: String? = null, // Task 11
)

class ComplaintFlowViewModel(app: Application) : AndroidViewModel(app) {
    private val playbook = ComplaintPlaybookLoader.bundled()
    private val identityStore = IdentityStore.create(app)
    private val _state = MutableStateFlow(ComplaintFlowState())
    val state: StateFlow<ComplaintFlowState> = _state.asStateFlow()

    private var draft: ComplaintDraft? = null

    /** Open with the session's draft + matched scam code (either may be null → user picks). */
    fun open(draft: ComplaintDraft?, scamCode: String?, moneyLost: Boolean) {
        this.draft = draft
        val dests = ComplaintRouter.route(playbook, scamCode, moneyLost)
        val selected = dests.firstOrNull()
        _state.value = ComplaintFlowState(destinations = dests, selected = selected)
        viewModelScope.launch { _state.value = _state.value.copy(identity = identityStore.get()) }

        // Task 11: background-only advisory — never delays PREPARE, which already rendered above.
        // Fails silent to null on no key/offline/any error (GeminiClient.checkPlaybookFreshness).
        if (selected != null) {
            viewModelScope.launch {
                val note = withContext(Dispatchers.IO) {
                    GeminiClient.checkPlaybookFreshness(selected.name, selected.url, playbook.verifiedOn)
                }
                // Race guard: open() replaces the whole state fresh, so only apply this result while
                // the destination it was computed for is still the one selected (the user could have
                // switched destinations, or re-opened the flow, while the call was in flight).
                if (note != null && _state.value.selected == selected) {
                    _state.value = _state.value.copy(freshnessNote = note)
                }
            }
        }

        // Same background-only, fails-silent pattern: rewrites the deterministic Caller/You transcript
        // into one coherent incident account instead of the raw line-by-line dump (owner-reported bug,
        // 2026-07-22: the report felt "generic" partly because it read like a pasted chat log). Fires
        // once per open(); the packet keeps showing the deterministic text — already real and safe —
        // until/unless this resolves, and forever if it never does.
        if (draft != null && selected != null && draft.narrative.text.isNotBlank()) {
            viewModelScope.launch {
                val polished = withContext(Dispatchers.IO) {
                    GeminiClient.draftIncidentNarrative(draft.narrative.text, draft.classification.scamName, selected.categoryValue)
                }
                // Race guard: only apply while this is still the open draft/destination AND the user
                // hasn't already moved on to FILE (the live government WebView) — past REVIEW, the
                // user has already reviewed and accepted the deterministic text; silently rewriting the
                // narrative under them there would fill a field they never actually saw.
                if (polished != null && this@ComplaintFlowViewModel.draft === draft &&
                    _state.value.selected == selected && _state.value.step != ComplaintStep.FILE
                ) {
                    this@ComplaintFlowViewModel.draft = draft.copy(narrative = draft.narrative.copy(text = polished))
                    reassemble()
                }
            }
        }
    }

    fun selectDestination(d: ComplaintDestination) { _state.value = _state.value.copy(selected = d) }

    fun toReview() {
        val d = _state.value.selected ?: return
        val dr = draft ?: return
        _state.value = _state.value.copy(
            step = ComplaintStep.REVIEW,
            packet = ComplaintPacketAssembler.assemble(d, dr, _state.value.identity, _state.value.loss),
        )
    }

    fun setLoss(loss: LossInput) { _state.value = _state.value.copy(loss = loss); reassemble() }

    fun saveIdentity(details: IdentityDetails) {
        _state.value = _state.value.copy(identity = details)
        viewModelScope.launch { identityStore.set(details) }
        reassemble()
    }

    fun toFile() { _state.value = _state.value.copy(step = ComplaintStep.FILE) }
    fun back() {
        _state.value = when (_state.value.step) {
            ComplaintStep.FILE -> _state.value.copy(step = ComplaintStep.REVIEW)
            ComplaintStep.REVIEW -> _state.value.copy(step = ComplaintStep.PREPARE)
            ComplaintStep.PREPARE -> _state.value
        }
    }

    private fun reassemble() {
        val d = _state.value.selected ?: return
        val dr = draft ?: return
        _state.value = _state.value.copy(
            packet = ComplaintPacketAssembler.assemble(d, dr, _state.value.identity, _state.value.loss),
        )
    }
}
