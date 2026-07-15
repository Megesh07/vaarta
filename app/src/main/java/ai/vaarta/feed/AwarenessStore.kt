package ai.vaarta.feed

import ai.vaarta.core.reasoning.AwarenessCard
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
 */
object AwarenessStore {

    private const val CACHE_FILE = "awareness_feed.json"
    private const val PREFS = "vaarta_awareness_prefs"
    private const val KEY_FETCHED_AT = "fetched_at"

    /** Feed is considered stale (worth a background refresh) after this long. */
    const val STALE_MS = 12 * 60 * 60 * 1000L // 12 hours

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val listSerializer = ListSerializer(AwarenessCard.serializer())

    data class Cached(val cards: List<AwarenessCard>, val fetchedAtMs: Long)

    /** The bundled offline fallback (~8 curated real India scams). Empty only if the asset is corrupt. */
    fun loadSeed(context: Context): List<AwarenessCard> = try {
        val text = context.assets.open("awareness_seed.json").bufferedReader().use { it.readText() }
        json.decodeFromString(listSerializer, text)
    } catch (e: Exception) {
        Log.w("AwarenessStore", "seed load failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    /** Last good fetched feed, or null if nothing has been cached yet. */
    fun readCache(context: Context): Cached? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return null
        return try {
            val cards = json.decodeFromString(listSerializer, file.readText())
            if (cards.isEmpty()) return null
            val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_FETCHED_AT, 0L)
            Cached(cards, at)
        } catch (e: Exception) {
            Log.w("AwarenessStore", "cache read failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Persists a freshly fetched feed and stamps [nowMs] as the fetch time. */
    fun writeCache(context: Context, cards: List<AwarenessCard>, nowMs: Long) {
        if (cards.isEmpty()) return
        try {
            File(context.filesDir, CACHE_FILE).writeText(json.encodeToString(listSerializer, cards))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_FETCHED_AT, nowMs).apply()
        } catch (e: Exception) {
            Log.w("AwarenessStore", "cache write failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
