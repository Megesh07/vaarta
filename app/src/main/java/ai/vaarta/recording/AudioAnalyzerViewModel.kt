package ai.vaarta.recording

import ai.vaarta.AudioScamAnalyzer
import ai.vaarta.ai.GeminiClient
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns recorded-call analysis (Phase 4D, ADR-0003). Separate from [ai.vaarta.SessionViewModel] (live,
 * Context-free) because it needs a Context to read the picked file's bytes via the ContentResolver.
 * The heavy work is a plain [AudioScamAnalyzer] (Context-free, unit-testable); this class only bridges
 * the picked [Uri] → bytes → analyzer → UI state, all off the main thread and fail-closed.
 */
class AudioAnalyzerViewModel(app: Application) : AndroidViewModel(app) {

    private val analyzer = AudioScamAnalyzer()

    /** The Analyze screen's state machine. [Done] carries the finished verdict; [Error] a user-facing line. */
    sealed interface UiState {
        data object Idle : UiState
        data object Running : UiState
        data class Done(val result: AudioScamAnalyzer.Result) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Analyze the recording at [uri] (from the system file picker). Idempotent per call; overwrites state. */
    fun analyze(uri: Uri) {
        if (!GeminiClient.isConfigured()) {
            _state.value = UiState.Error(
                "Recording analysis needs the AI layer, which isn't configured in this build.",
            )
            return
        }
        _state.value = UiState.Running
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { runAnalysis(uri) }
        }
    }

    private fun runAnalysis(uri: Uri): UiState {
        val resolver = getApplication<Application>().contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return UiState.Error("Couldn't read that recording. Try picking it again.")
        if (bytes.isEmpty()) return UiState.Error("That file is empty.")
        if (bytes.size > GeminiClient.MAX_INLINE_AUDIO_BYTES) {
            val mb = GeminiClient.MAX_INLINE_AUDIO_BYTES / (1024 * 1024)
            return UiState.Error("This clip is too large (over $mb MB). Please use a shorter recording.")
        }
        val mime = normalizeMime(resolver.getType(uri))
        val result = analyzer.analyze(bytes, mime)
            ?: return UiState.Error(
                "Couldn't analyze this recording — the AI didn't return a usable result. " +
                    "Check your connection, or the clip may be in an unsupported format.",
            )
        return UiState.Done(result)
    }

    fun reset() { _state.value = UiState.Idle }

    /** Keep the resolver's audio type if it looks usable; otherwise default to a broadly-accepted one. */
    private fun normalizeMime(raw: String?): String =
        if (raw != null && raw.startsWith("audio/")) raw else "audio/mpeg"
}
