package ai.vaarta.history

import ai.vaarta.ChatItem
import ai.vaarta.core.data.HistoryRepository
import ai.vaarta.core.data.db.CallSessionEntity
import ai.vaarta.core.data.db.SessionSource
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the encrypted saved history (Phase 4B, ADR-0004). Separate from [ai.vaarta.SessionViewModel]
 * (which stays RAM-only, Context-free, the deterministic brain) — persistence needs a Context (Keystore
 * + Room) and its own lifecycle, and only ever runs on explicit user action to save/view/delete.
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HistoryRepository.create(app)
    private val prefs = app.getSharedPreferences("vaarta_history_prefs", Application.MODE_PRIVATE)

    /** Newest-first saved sessions for the home history list. */
    val sessions: StateFlow<List<CallSessionEntity>> =
        repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Retention in days; 0 = keep forever (default). Persisted across launches. */
    private val _retentionDays = MutableStateFlow(prefs.getInt(KEY_RETENTION_DAYS, 0))
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    init {
        // Enforce retention on launch (deletes sessions older than the cutoff; cascades their turns).
        viewModelScope.launch {
            repo.applyRetention(System.currentTimeMillis(), _retentionDays.value)
        }
    }

    /**
     * Persist a completed session (explicit user action = consent to save). Writes the session row,
     * its turns, and finalizes the header in one background pass. No-op for an empty thread.
     */
    fun save(
        source: SessionSource,
        finalScore: Int,
        finalLevel: String,
        scamType: String?,
        chat: List<ChatItem>,
        onSaved: () -> Unit = {},
    ) {
        if (chat.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = repo.startSession(startedAtMs = now, source = source)
            chat.toTurnEntities(sessionId = id, baseAtMs = now).forEach { repo.appendTurn(it) }
            repo.finalizeSession(
                sessionId = id,
                endedAtMs = System.currentTimeMillis(),
                finalScore = finalScore,
                finalLevel = finalLevel,
                scamType = scamType,
                language = null,
            )
            withContext(Dispatchers.Main) { onSaved() }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteSession(id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repo.deleteAll()
        }
    }

    fun setRetentionDays(days: Int) {
        _retentionDays.value = days
        prefs.edit().putInt(KEY_RETENTION_DAYS, days).apply()
        viewModelScope.launch { repo.applyRetention(System.currentTimeMillis(), days) }
    }

    private companion object {
        const val KEY_RETENTION_DAYS = "retention_days"
    }
}
