package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.ComplaintDestination
import ai.vaarta.core.reasoning.ComplaintPlaybookLoader
import ai.vaarta.core.reasoning.ComplaintRouter
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        _state.value = ComplaintFlowState(destinations = dests, selected = dests.firstOrNull())
        viewModelScope.launch { _state.value = _state.value.copy(identity = identityStore.get()) }
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
