package ai.vaarta.feed

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.i18n.AppLanguage
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
            // 2) Refresh from the web on every open so the feed is genuinely current (not just when
            //    stale) — the user wants real, live trending articles each time. Fails closed: on any
            //    error the instantly-painted cache/seed stays. Cache is only for the instant first paint.
            if (GeminiClient.isConfigured()) refresh()
        }
    }

    /**
     * Force a web refresh (also the pull/tap-to-refresh affordance). Two-phase so the user sees
     * content fast: (1) the real headlines + links land immediately, then (2) each real article's own
     * og:title/og:image/publisher fills in from its page. Keeps the current cards on failure.
     */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { GeminiClient.awarenessFeed() }
            if (fresh.isNullOrEmpty()) {
                _state.value = _state.value.copy(refreshing = false)
                return@launch
            }
            // Phase 1: show real headlines + links now (photos still resolving → spinner stays).
            _state.value = FeedState(fresh, Origin.LIVE, refreshing = true)
            // Phase 2: enrich each real-article card with its page's own image/headline/publisher.
            enrichWithOg(fresh)
        }
    }

    /** Resolves each real-article card's og:image/og:title/og:site_name in parallel (fail-closed per
     *  card), then publishes the enriched feed and caches it so a re-open shows photos instantly. */
    private suspend fun enrichWithOg(cards: List<AwarenessCard>) = coroutineScope {
        val ctx = getApplication<Application>()
        val language = AppLanguage.current()
        val enriched = cards.map { card ->
            async(Dispatchers.IO) {
                val url = card.url ?: return@async card
                val og = OgImageResolver.resolve(url) ?: return@async card
                card.copy(
                    title = og.title?.takeIf { it.isNotBlank() } ?: card.title,
                    imageUrl = og.imageUrl ?: card.imageUrl,
                    sourceName = og.siteName?.takeIf { it.isNotBlank() } ?: card.sourceName,
                )
            }
        }.awaitAll()
        _state.value = FeedState(enriched, Origin.LIVE, refreshing = false)
        withContext(Dispatchers.IO) {
            AwarenessStore.writeCache(ctx, language, enriched, System.currentTimeMillis())
        }
    }
}
