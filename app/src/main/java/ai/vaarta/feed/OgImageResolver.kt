package ai.vaarta.feed

import ai.vaarta.core.reasoning.OgTagParser
import ai.vaarta.core.reasoning.OgTags
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves a real article's own preview image + headline + publisher by fetching the page and reading
 * its Open Graph / Twitter-card tags (live-news feed redesign 2026-07-21). This is what turns a feed
 * card from a generic illustration into the actual article's photo and real headline.
 *
 * Every step FAILS CLOSED: a network error, non-200, timeout, or a page with no og tags returns null,
 * and the card simply keeps its illustration + model caption. Results (including "nothing found") are
 * cached per URL so re-opening Home never re-scrapes. Blocking — call off the main thread.
 *
 * Only the first [MAX_BYTES] of the response are read (og tags live in <head>, near the top), so a
 * heavy article page can't stall the feed or blow memory.
 */
object OgImageResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)       // grounding URIs are Google redirects → follow to the real page
        .followSslRedirects(true)
        .build()

    private const val MAX_BYTES = 300_000L

    // url -> resolved tags; EMPTY marks "fetched, nothing usable" so we don't retry a dead page.
    private val cache = ConcurrentHashMap<String, OgTags>()
    private val EMPTY = OgTags()

    /** Returns the page's [OgTags], or null on any failure / if nothing usable was found. Cached. */
    fun resolve(url: String): OgTags? {
        if (url.isBlank()) return null
        cache[url]?.let { return it.takeIf { c -> c !== EMPTY } }
        val og = fetch(url)
        cache[url] = og ?: EMPTY
        return og
    }

    private fun fetch(url: String): OgTags? = try {
        val request = Request.Builder()
            .url(url)
            // A real UA — some publishers serve a bare page (or 403) to an unknown client.
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 VAARTA/1.0")
            .header("Accept", "text/html")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) return null
            val source = body.source()
            source.request(MAX_BYTES) // buffer up to MAX_BYTES (or EOF), whichever comes first
            val take = minOf(source.buffer.size, MAX_BYTES)
            val html = source.buffer.readUtf8(take)
            OgTagParser.parse(html)
        }
    } catch (e: Exception) {
        // Type only — never the URL body or PII. A failed scrape is normal (blocked/paywalled).
        Log.w("OgImageResolver", "og fetch failed: ${e.javaClass.simpleName}")
        null
    }
}
