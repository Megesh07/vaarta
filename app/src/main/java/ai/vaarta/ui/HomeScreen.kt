package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.ui.components.ChipTone
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.EmptyState
import ai.vaarta.ui.components.IconChipCard
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The clean landing screen (spec §4.1). One visually-dominant PANIC control in the thumb zone, calm
 * action cards, and the AI web-grounded trending-scams feed. No Manual Mode. Strong red is reserved
 * for the panic context only (60/30/10 — red means real danger, never decoration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    aiConfigured: Boolean,
    onStartLive: () -> Unit,
    onAnalyzeRecording: () -> Unit,
    onAskVaarta: () -> Unit,
    onOpenUrl: (String) -> Unit,
    feedCards: List<AwarenessCard>,
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
            Column {
                Text("VAARTA", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text(
                    "Your guardian against phone scams",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                )
            }

            // PANIC — the one dominant control. Big, unmistakable, thumb-reachable.
            Card(
                colors = CardDefaults.cardColors(containerColor = c.scam),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clickable { showPanic = true }
                    .semantics { contentDescription = "I am on a scam call right now. Open emergency steps." },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(VSpace.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VSpace.lg),
                ) {
                    VaartaIcon(R.drawable.ic_siren, contentDescription = null, tint = Color.White, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "I'm on a scam call right now",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            "Tap for what to do this second",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }

            // Calm action cards — brand-tinted icon chips.
            IconChipCard(
                icon = R.drawable.ic_mic,
                title = "Help me on a call",
                subtitle = "VAARTA listens on speaker and coaches you live",
                onClick = onStartLive,
            )
            IconChipCard(
                icon = R.drawable.ic_nav_chat,
                title = "Ask VAARTA",
                subtitle = "Chat about a message or call — is it a scam?",
                onClick = onAskVaarta,
            )
            if (aiConfigured) {
                IconChipCard(
                    icon = R.drawable.ic_headphones,
                    title = "Check a recording",
                    subtitle = "Analyze a call you already recorded",
                    onClick = onAnalyzeRecording,
                )
            }

            // Trending scams — AI-generated, web-grounded feed (spec §6.1). Sits below the actions
            // and never competes with them.
            Spacer(Modifier.height(VSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                Text("Trending scams in India", style = MaterialTheme.typography.titleLarge, color = c.ink)
                if (feedRefreshing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "Tap a card — VAARTA explains it in plain language, with sources.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            if (feedCards.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_globe,
                    text = "Scam-awareness stories will appear here once you're online.",
                )
            } else {
                feedCards.forEach { card -> AwarenessCardRow(card, onClick = { onOpenArticle(card) }) }
            }
            Spacer(Modifier.height(VSpace.xxl))
        }
    }

    if (showPanic) {
        ModalBottomSheet(
            onDismissRequest = { showPanic = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl),
                verticalArrangement = Arrangement.spacedBy(VSpace.md),
            ) {
                Text("Do this right now", style = MaterialTheme.typography.headlineSmall, color = c.scam)
                PanicStep("1", "Don't pay anyone. No real officer or bank asks for money on a call.")
                PanicStep("2", "Never share an OTP, PIN, or password. Ever.")
                PanicStep("3", "Hang up. It is safe to end the call — no one is arrested over a phone call.")
                PanicStep("4", "Call 1930 (the government cyber-crime helpline).")
                Spacer(Modifier.height(VSpace.xs))
                VaartaButton(
                    text = "Call 1930 now",
                    onClick = { onOpenUrl("tel:1930") },
                    leadingIcon = R.drawable.ic_phone,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                VaartaSecondaryButton(
                    text = "Start live help",
                    onClick = { showPanic = false; onStartLive() },
                    leadingIcon = R.drawable.ic_mic,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (aiConfigured) {
                    VaartaSecondaryButton(
                        text = "Analyze a recording",
                        onClick = { showPanic = false; onAnalyzeRecording() },
                        leadingIcon = R.drawable.ic_headphones,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A trending-scam story — neutral (quiet) icon chip, category eyebrow, title, one-line, chevron. */
@Composable
private fun AwarenessCardRow(card: AwarenessCard, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) androidx.compose.foundation.BorderStroke(1.dp, c.line) else null,
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
            Surface(color = c.track, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(R.drawable.ic_alert_triangle, contentDescription = null, tint = c.muted, size = 22.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                if (card.scamType.isNotBlank()) {
                    Eyebrow(card.scamType, color = c.indigo)
                    Spacer(Modifier.height(2.dp))
                }
                Text(card.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(card.oneLine, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2)
            }
            VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 20.dp)
        }
    }
}

@Composable
private fun PanicStep(number: String, text: String) {
    val c = VaartaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
        Surface(color = c.scamTint, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.titleMedium, color = c.scam)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
    }
}
