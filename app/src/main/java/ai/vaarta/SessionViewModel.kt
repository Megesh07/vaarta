package ai.vaarta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/**
 * Thin ViewModel wrapper around [CopilotSession] (Phase 4C). All the live-copilot pipeline, engine
 * bridging, and state now live in [CopilotSession] so the exact same logic can also run inside the
 * floating-overlay foreground service. This wrapper only binds one session to `viewModelScope` and
 * tears it down with the ViewModel; observe its state via `vm.session.<flow>`.
 *
 * One instance == one in-app call session; state lives in RAM only (DATABASE_DESIGN.md §2).
 */
class SessionViewModel : ViewModel() {

    val session = CopilotSession(viewModelScope)

    override fun onCleared() {
        session.close()
        super.onCleared()
    }
}
