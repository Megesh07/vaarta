package ai.vaarta.feed

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.i18n.AppLanguage
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the home "Trending scams" education feed (v2, spec §6.1). On creation it shows the best data it
 * already has — cached feed, or the bundled seed — *instantly*, then refreshes from the web in the
 * background when the cache is missing or stale. Every failure is silent and safe: the user keeps
 * seeing the last good feed (or the seed), never an empty screen and never a fabricated one.
 */
class AwarenessViewModel(app: Application) : AndroidViewModel(app) {

    /** Where the currently-shown cards came from — drives an honest, unobtrusive status line. */
    enum class Origin { SEED, CACHED, LIVE }

    data class FeedState(
        val cards: List<AwarenessCard> = emptyList(),
        val origin: Origin = Origin.SEED,
        val refreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val language = AppLanguage.current()
            // 1) Show something immediately: cache if present (for this language), else the seed.
            val cached = withContext(Dispatchers.IO) { AwarenessStore.readCache(ctx, language) }
            if (cached != null) {
                _state.value = FeedState(cached.cards, Origin.CACHED, refreshing = false)
            } else {
                val seed = withContext(Dispatchers.IO) { AwarenessStore.loadSeed(ctx) }
                _state.value = FeedState(seed, Origin.SEED, refreshing = false)
            }
            // 2) Refresh from the web if we have no cache or it's stale. GeminiClient fails closed.
            val now = System.currentTimeMillis()
            val stale = cached == null || (now - cached.fetchedAtMs) > AwarenessStore.STALE_MS
            if (stale && GeminiClient.isConfigured()) refresh()
        }
    }

    /** Force a web refresh (also used by a pull/refresh affordance). Keeps current cards on failure. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val language = AppLanguage.current()
            val fresh = withContext(Dispatchers.IO) { GeminiClient.awarenessFeed() }
            if (!fresh.isNullOrEmpty()) {
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) { AwarenessStore.writeCache(ctx, language, fresh, now) }
                _state.value = FeedState(fresh, Origin.LIVE, refreshing = false)
            } else {
                _state.value = _state.value.copy(refreshing = false)
            }
        }
    }
}
