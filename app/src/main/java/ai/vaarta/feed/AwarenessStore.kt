package ai.vaarta.feed

import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.i18n.AppLanguage
import android.content.Context
import android.util.Log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local persistence for the home education feed (v2, spec §6.1). The feed is public, non-sensitive
 * scam headlines, so it is cached as plain JSON in app files (NOT the encrypted Room DB — no personal
 * data here) with a fetched-date, plus a bundled seed in assets that guarantees the screen is never
 * empty offline or on a cold first launch. All reads/writes are blocking — call off the main thread.
 *
 * The cache is keyed by [AppLanguage] tag (spec §3B.2: generated content follows the UI language, and
 * "feed + article caches are keyed by language and invalidated on language change") — switching the
 * UI language naturally lands on a different (or empty) cache file, no explicit invalidation needed.
 * The bundled seed stays English-only for now (Tier-1 translation of its ~8 cards is tracked as a
 * follow-up, not this pass) — a non-English UI simply sees the English seed until the first live
 * refresh succeeds, the same graceful degradation Android's own string-resource fallback uses.
 */
object AwarenessStore {

    private const val PREFS = "vaarta_awareness_prefs"

    /** Feed is considered stale (worth a background refresh) after this long. */
    const val STALE_MS = 12 * 60 * 60 * 1000L // 12 hours

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val listSerializer = ListSerializer(AwarenessCard.serializer())

    data class Cached(val cards: List<AwarenessCard>, val fetchedAtMs: Long)

    private fun cacheFile(context: Context, language: AppLanguage): File =
        File(context.filesDir, "awareness_feed_${language.tag}.json")

    private fun fetchedAtKey(language: AppLanguage): String = "fetched_at_${language.tag}"

    /** The bundled offline fallback (~8 curated real India scams). Empty only if the asset is corrupt. */
    fun loadSeed(context: Context): List<AwarenessCard> = try {
        val text = context.assets.open("awareness_seed.json").bufferedReader().use { it.readText() }
        json.decodeFromString(listSerializer, text)
    } catch (e: Exception) {
        Log.w("AwarenessStore", "seed load failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    /** Last good fetched feed for [language], or null if nothing has been cached yet in that language. */
    fun readCache(context: Context, language: AppLanguage): Cached? {
        val file = cacheFile(context, language)
        if (!file.exists()) return null
        return try {
            val cards = json.decodeFromString(listSerializer, file.readText())
            if (cards.isEmpty()) return null
            val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(fetchedAtKey(language), 0L)
            Cached(cards, at)
        } catch (e: Exception) {
            Log.w("AwarenessStore", "cache read failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Persists a freshly fetched feed for [language] and stamps [nowMs] as the fetch time. */
    fun writeCache(context: Context, language: AppLanguage, cards: List<AwarenessCard>, nowMs: Long) {
        if (cards.isEmpty()) return
        try {
            cacheFile(context, language).writeText(json.encodeToString(listSerializer, cards))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(fetchedAtKey(language), nowMs).apply()
        } catch (e: Exception) {
            Log.w("AwarenessStore", "cache write failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
