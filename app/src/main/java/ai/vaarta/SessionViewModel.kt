package ai.vaarta

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

/**
 * Thin ViewModel wrapper around [CopilotSession] (Phase 4C). All the live-copilot pipeline, engine
 * bridging, and state now live in [CopilotSession] so the exact same logic can also run inside the
 * floating-overlay foreground service. This wrapper only binds one session to `viewModelScope` and
 * tears it down with the ViewModel; observe its state via `vm.session.<flow>`.
 *
 * One instance == one in-app call session; state lives in RAM only (DATABASE_DESIGN.md §2).
 *
 * AndroidViewModel (not plain ViewModel) since Part D's speaker-attribution wiring needs a Context
 * (SpeakerEmbedder's asset manager, VaartaDatabase.get) — `getApplication()` supplies it without this
 * class taking on any Activity lifecycle dependency.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    val session = CopilotSession(viewModelScope, application)

    override fun onCleared() {
        session.close()
        super.onCleared()
    }
}
