package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.ArticleSummary
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.core.reasoning.StructuredSummary
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.share.BilingualShare
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.ShimmerLines
import ai.vaarta.ui.components.SourceLink
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.vaartaPressable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Article v2 (redesign spec §6.4): cover banner + title render instantly from the card (the page
 * is worth looking at before the AI answers — shimmer stands in for the summary only), then the
 * structured What-it-is / How-to-spot / What-to-do sections (spec §7) with real cited sources.
 * Prose fallback keeps the old MarkdownText path; total failure falls back to the card's one-line.
 * One primary action; the warn-family share moved to the top bar.
 */
@Composable
fun ArticleScreen(
    card: AwarenessCard,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    onAskAbout: (seed: String, topic: String) -> Unit,
    modifier: Modifier = Modifier,
    relatedPool: List<AwarenessCard> = emptyList(),
    onOpenArticle: (AwarenessCard) -> Unit = {},
) {
    // Live-news redesign (2026-07-21): a card backed by a REAL article URL opens the real article
    // in-app, with an AI "ask/summarize about this article" action and a Related row. A seed/offline
    // card (no URL) keeps the AI-summary page below.
    if (!card.url.isNullOrBlank()) {
        LiveArticleView(card, relatedPool, onBack, onOpenUrl, onShare, onAskAbout, onOpenArticle, modifier)
        return
    }
    val onAskAboutThis: (String) -> Unit = { seed -> onAskAbout(seed, card.title) }
    val c = VaartaTheme.colors
    var summary by remember(card.title) { mutableStateOf<ArticleSummary?>(null) }
    var loading by remember(card.title) { mutableStateOf(true) }

    LaunchedEffect(card.title) {
        loading = true
        val result = withContext(Dispatchers.IO) { GeminiClient.summarizeArticle(card.title, card.scamType) }
        summary = result ?: ArticleSummary(card.oneLine)
        loading = false
    }

    val summaryText = summary?.let { s ->
        s.structured?.let { st ->
            (listOf(st.whatItIs) + st.whatToDo.mapIndexed { i, step -> "${i + 1}. $step" }).joinToString("\n")
        } ?: s.text.takeIf { it.isNotBlank() }
    } ?: card.oneLine

    val seedContext = buildString {
        append("The user is reading a scam-awareness article titled \"${card.title}\"")
        if (card.scamType.isNotBlank()) append(" (category: ${card.scamType})")
        append(".\n")
        append(summaryText)
    }

    val shareA11y = stringResource(R.string.article_share_a11y)
    val warnPrefix = stringResource(R.string.article_warn_prefix, card.title)
    val warnSuffix = stringResource(R.string.article_warn_suffix)
    VaartaSubScreen(
        title = null,
        onBack = onBack,
        modifier = modifier,
        trailing = {
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier.size(44.dp).vaartaPressable(
                    onClick = { onShare(BilingualShare.compose(warnFamilyText(card, summaryText, warnPrefix, warnSuffix), AppLanguage.current())) },
                    enabled = !loading,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(R.drawable.ic_bell, contentDescription = shareA11y, tint = c.ink, size = 22.dp)
                }
            }
        },
        bottomContent = {
            VaartaButton(
                text = stringResource(R.string.article_ask),
                onClick = { onAskAboutThis(seedContext) },
                leadingIcon = R.drawable.ic_sparkle,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        // Cover banner + category pill — always instant (they come from the card, not the AI).
        Box {
            ScamCover(
                "${card.scamType} ${card.title}",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f),
                corner = 16.dp,
            )
            if (card.scamType.isNotBlank()) {
                Surface(
                    color = Color(0xF2FFFFFF),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(VSpace.md),
                ) {
                    Text(
                        card.scamType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3B35A8),
                        modifier = Modifier.padding(horizontal = VSpace.md, vertical = 5.dp),
                    )
                }
            }
        }
        Column {
            Text(card.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            if (card.sourceName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.home_feed_seen_in, card.sourceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }

        when {
            loading -> {
                Text(stringResource(R.string.article_reading), style = MaterialTheme.typography.bodySmall, color = c.faint)
                ShimmerLines(lines = 3)
                Spacer(Modifier.height(VSpace.sm))
                ShimmerLines(lines = 3)
            }
            summary?.structured != null -> StructuredSections(summary!!.structured!!)
            else -> MarkdownText(summary?.text.orEmpty(), color = c.ink)
        }

        summary?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
            Spacer(Modifier.height(VSpace.xs))
            Eyebrow(stringResource(R.string.article_sources))
            for (s in sources.take(4)) {
                SourceLink(title = s.title, onClick = { onOpenUrl(s.uri) })
            }
        }
        Spacer(Modifier.height(VSpace.sm))
    }
}

/** The designed three-part read (spec §7): prose, check-glyph signs, numbered steps. */
@Composable
private fun StructuredSections(s: StructuredSummary) {
    val c = VaartaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(VSpace.lg)) {
        Column {
            Eyebrow(stringResource(R.string.article_what_it_is), color = c.indigo)
            Spacer(Modifier.height(VSpace.xs))
            Text(s.whatItIs, style = MaterialTheme.typography.bodyLarge, color = c.ink)
        }
        Column {
            Eyebrow(stringResource(R.string.article_how_to_spot), color = c.indigo)
            Spacer(Modifier.height(VSpace.sm))
            Column(verticalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                for (sign in s.howToSpot) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                        VaartaIcon(
                            R.drawable.ic_alert_triangle, contentDescription = null,
                            tint = c.caution, size = 18.dp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(sign, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
            }
        }
        Column {
            Eyebrow(stringResource(R.string.article_what_to_do), color = c.indigo)
            Spacer(Modifier.height(VSpace.sm))
            Column(verticalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                s.whatToDo.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
                        Surface(color = c.indigoTint, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.indigoInk)
                            }
                        }
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Shareable plain-text warning built from the topic + summary, with the 1930 call-to-action. */
private fun warnFamilyText(card: AwarenessCard, summaryText: String, prefix: String, suffix: String): String = buildString {
    append(prefix)
    append(summaryText)
    append(suffix)
}

// ---------------------------------------------------------------------------------------------------
// Live article (2026-07-21): the REAL article rendered in-app, with an AI "ask/summarize about this
// article" action and a Related row. Only reached when the card has a real grounding-cited URL.
// ---------------------------------------------------------------------------------------------------

@Composable
private fun LiveArticleView(
    card: AwarenessCard,
    relatedPool: List<AwarenessCard>,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    onAskAbout: (seed: String, topic: String) -> Unit,
    onOpenArticle: (AwarenessCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val url = card.url!!
    val related = remember(card, relatedPool) { relatedArticles(relatedPool, card) }

    // The AI seed: the chat is grounded, so this one context covers BOTH "summarize it" and any
    // follow-up question the user asks about this specific article.
    val seedContext = buildString {
        append("The user is reading this scam-news article: \"${card.title}\"")
        if (card.scamType.isNotBlank()) append(" (category: ${card.scamType})")
        append(" — ").append(url)
        append(". Summarize it in plain language and answer their questions about it.")
    }

    val openBrowserA11y = stringResource(R.string.article_open_browser)
    VaartaSubScreen(
        title = null,
        onBack = onBack,
        modifier = modifier,
        scrollable = false, // the WebView owns scrolling — never nest it in a scroll container
        trailing = {
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier.size(44.dp).vaartaPressable(onClick = { onOpenUrl(url) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(R.drawable.ic_globe, contentDescription = openBrowserA11y, tint = c.ink, size = 22.dp)
                }
            }
        },
        bottomContent = {
            if (related.isNotEmpty()) {
                Eyebrow(stringResource(R.string.article_related))
                RelatedRow(related, onOpenArticle)
            }
            VaartaButton(
                text = stringResource(R.string.article_ask),
                onClick = { onAskAbout(seedContext, card.title) },
                leadingIcon = R.drawable.ic_sparkle,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        // Headline + source above the reader so the real article is identified even before it paints.
        Column(Modifier.padding(horizontal = VSpace.xl, vertical = VSpace.sm)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.sourceName.isNotBlank()) {
                Text(
                    stringResource(R.string.home_feed_seen_in, card.sourceName),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
        WebViewArticle(url, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** The real article page, rendered by the system WebView. JS + DOM storage on so modern news sites
 *  render; the returned view owns its own scrolling. Load failure is recoverable via "Open in
 *  browser" (the top-bar action) — we never trap the user on a blank page. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewArticle(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                loadUrl(url)
            }
        },
    )
}

/** Horizontal strip of other current articles, same scam category first. Illustration thumbs keep it
 *  light (no extra image fetches on the article screen). */
@Composable
private fun RelatedRow(related: List<AwarenessCard>, onOpenArticle: (AwarenessCard) -> Unit) {
    val c = VaartaTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
    ) {
        for (item in related) {
            Column(
                Modifier.width(120.dp).vaartaPressable(onClick = { onOpenArticle(item) }),
                verticalArrangement = Arrangement.spacedBy(VSpace.xs),
            ) {
                ScamCover(
                    "${item.scamType} ${item.title}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                    corner = 12.dp,
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Related selection: other articles, same [AwarenessCard.scamType] first, current one excluded. */
private fun relatedArticles(pool: List<AwarenessCard>, current: AwarenessCard, max: Int = 6): List<AwarenessCard> {
    val others = pool.filter { it.url != current.url || it.title != current.title }
    val sameType = others.filter { current.scamType.isNotBlank() && it.scamType.equals(current.scamType, ignoreCase = true) }
    return (sameType + (others - sameType.toSet())).take(max)
}
