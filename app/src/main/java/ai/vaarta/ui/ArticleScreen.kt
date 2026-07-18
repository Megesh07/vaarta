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
    onAskAbout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                onClick = { onAskAbout(seedContext) },
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
