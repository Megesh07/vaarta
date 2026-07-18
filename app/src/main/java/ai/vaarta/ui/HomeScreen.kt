package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.feed.AwarenessViewModel
import ai.vaarta.ui.components.ActionTile
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.EmptyState
import ai.vaarta.ui.components.IconChipCard
import ai.vaarta.ui.components.PanicSheet
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Home v2 (redesign spec §6.1): brand header + honest status chip, one slim PANIC banner (the only
 * red on screen), the tile grammar (one wide primary + two compact tiles), and the magazine feed —
 * a featured cover-banner story + compact thumbnail rows. Strong red is reserved for the panic
 * context only; everything else is indigo/neutral chrome.
 */
@Composable
fun HomeScreen(
    aiConfigured: Boolean,
    onStartLive: () -> Unit,
    onAnalyzeRecording: () -> Unit,
    onAskVaarta: () -> Unit,
    onOpenUrl: (String) -> Unit,
    feedCards: List<AwarenessCard>,
    feedOrigin: AwarenessViewModel.Origin,
    feedRefreshing: Boolean,
    onOpenArticle: (AwarenessCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val scroll = rememberScrollState()
    var showPanic by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = VSpace.xl),
            verticalArrangement = Arrangement.spacedBy(VSpace.lg),
        ) {
            Spacer(Modifier.height(VSpace.sm))

            // Brand header + honest status chip. The old tagline is gone — content explains the app.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Spacer(Modifier.weight(1f))
                StatusChip(aiConfigured)
            }

            // PANIC — slim, unmistakable, the only red on screen.
            Card(
                colors = CardDefaults.cardColors(containerColor = c.scam),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable { showPanic = true },
            ) {
                val a11y = stringResource(R.string.home_panic_a11y)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = VSpace.xl)
                        .semantics { contentDescription = a11y },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VSpace.md),
                ) {
                    VaartaIcon(R.drawable.ic_siren, contentDescription = null, tint = Color.White, size = 26.dp)
                    Text(
                        stringResource(R.string.home_panic_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = Color.White, size = 20.dp)
                }
            }

            // The tile grammar: live help leads, the two quick actions share a row.
            IconChipCard(
                icon = R.drawable.ic_mic,
                title = stringResource(R.string.home_action_live_title),
                subtitle = stringResource(R.string.home_action_live_subtitle),
                onClick = onStartLive,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VSpace.md), modifier = Modifier.fillMaxWidth()) {
                ActionTile(
                    icon = R.drawable.ic_nav_chat,
                    title = stringResource(R.string.home_action_ask),
                    onClick = onAskVaarta,
                    modifier = Modifier.weight(1f),
                )
                if (aiConfigured) {
                    ActionTile(
                        icon = R.drawable.ic_headphones,
                        title = stringResource(R.string.home_action_recording),
                        onClick = onAnalyzeRecording,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Trending scams — the magazine feed (spec §5.2). Featured story + compact rows.
            Spacer(Modifier.height(VSpace.xs))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                    Text(stringResource(R.string.home_feed_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                    if (feedRefreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    when (feedOrigin) {
                        AwarenessViewModel.Origin.LIVE -> stringResource(R.string.home_feed_origin_live)
                        AwarenessViewModel.Origin.CACHED -> stringResource(R.string.home_feed_origin_cached)
                        AwarenessViewModel.Origin.SEED -> stringResource(R.string.home_feed_origin_seed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.faint,
                )
            }
            if (feedCards.isEmpty()) {
                EmptyState(icon = R.drawable.ic_globe, text = stringResource(R.string.home_feed_empty))
            } else {
                FeaturedScamCard(feedCards.first(), onClick = { onOpenArticle(feedCards.first()) })
                feedCards.drop(1).forEach { card -> AwarenessCardRow(card, onClick = { onOpenArticle(card) }) }
            }
            Spacer(Modifier.height(VSpace.xxl))
        }
    }

    if (showPanic) {
        PanicSheet(
            onDismissRequest = { showPanic = false },
            onOpenUrl = onOpenUrl,
            onStartLive = onStartLive,
        )
    }
}

/** Honest capability chip: cloud AI configured vs fully on-device. Quiet indigo chrome. */
@Composable
private fun StatusChip(aiConfigured: Boolean) {
    val c = VaartaTheme.colors
    Surface(color = c.indigoTint, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = VSpace.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VSpace.xs),
        ) {
            VaartaIcon(
                if (aiConfigured) R.drawable.ic_sparkle else R.drawable.ic_nav_shield,
                contentDescription = null,
                tint = c.indigoInk,
                size = 14.dp,
            )
            Text(
                stringResource(if (aiConfigured) R.string.home_status_ai_ready else R.string.home_status_on_device),
                style = MaterialTheme.typography.labelMedium,
                color = c.indigoInk,
            )
        }
    }
}

/** The featured story — full-width cover banner with an overlaid category pill (spec §5.2). */
@Composable
private fun FeaturedScamCard(card: AwarenessCard, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) BorderStroke(1.dp, c.line) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${card.title}. ${card.oneLine}" },
    ) {
        Column {
            Box {
                ScamCover(
                    "${card.scamType} ${card.title}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f),
                    corner = 0.dp,
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
                            color = VaartaLightIndigo,
                            modifier = Modifier.padding(horizontal = VSpace.md, vertical = 5.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(VSpace.lg)) {
                Text(card.title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2)
                if (card.sourceName.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.home_feed_seen_in, card.sourceName),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }
        }
    }
}

// The pill sits on the cover art (always indigo), so its ink is theme-independent.
private val VaartaLightIndigo = Color(0xFF3B35A8)

/** A compact trending story: cover thumb, category eyebrow, title. No body preview (spec §5.2). */
@Composable
private fun AwarenessCardRow(card: AwarenessCard, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) BorderStroke(1.dp, c.line) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${card.title}. ${card.oneLine}" },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(VSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VSpace.md),
        ) {
            ScamCover("${card.scamType} ${card.title}", modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                if (card.scamType.isNotBlank()) {
                    Eyebrow(card.scamType, color = c.indigo)
                    Spacer(Modifier.height(2.dp))
                }
                Text(card.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2)
            }
            VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 20.dp)
        }
    }
}
