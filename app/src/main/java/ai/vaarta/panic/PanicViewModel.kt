package ai.vaarta.panic

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.PanicContextSelector
import ai.vaarta.core.reasoning.PanicPersonalization
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the panic sheet shows beyond the always-present base steps (redesign spec §A2). */
data class PanicUiState(
    val loading: Boolean = false,
    val personalization: PanicPersonalization? = null,
)

/**
 * Drives the panic sheet's AI personalization layer (redesign spec §A2). The base [ai.vaarta.ui
 * .components.RightNowSteps] render instantly and unconditionally — this ViewModel only ever ADDS
 * a tailored heading + steps on top, in parallel, and is never on the critical path: no context,
 * no key, no network, or any failure all resolve to "no personalization", never a blocked sheet.
 */
class PanicViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PanicUiState())
    val state: StateFlow<PanicUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Call when the panic sheet opens. Picks the best available real context (live call > most
     * recent saved call/recording/chat > none) and, only if one exists, fires the personalization
     * call in the background. A fresh call always starts from a clean loading state.
     */
    fun open(
        liveScamType: String?,
        liveRiskLevel: String,
        recentScamType: String?,
        recentRiskLevel: String?,
    ) {
        job?.cancel()
        val contextLine = PanicContextSelector.select(liveScamType, liveRiskLevel, recentScamType, recentRiskLevel)
        if (contextLine == null || !GeminiClient.isConfigured()) {
            _state.value = PanicUiState()
            return
        }
        _state.value = PanicUiState(loading = true)
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { GeminiClient.personalizePanic(contextLine) }
            _state.value = PanicUiState(loading = false, personalization = result)
        }
    }

    /** Call when the panic sheet closes, so a stale result never flashes on the next open. */
    fun dismiss() {
        job?.cancel()
        _state.value = PanicUiState()
    }
}
