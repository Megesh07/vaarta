package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.ArticleSummary
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.SourceLink
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaBackBar
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Article summary screen (v2, spec §6.1). Opened from a home feed card: a clean banner (title +
 * scam-type + source label), then a plain-language AI summary of the *real* topic grounded on current
 * web sources — rendered through [MarkdownText] so no raw markup ever shows — with the cited sources
 * shown and tappable. Fails closed to the card's one-line if summarization errors.
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
    val scroll = rememberScrollState()
    var summary by remember(card.title) { mutableStateOf<ArticleSummary?>(null) }
    var loading by remember(card.title) { mutableStateOf(true) }

    LaunchedEffect(card.title) {
        loading = true
        val result = withContext(Dispatchers.IO) { GeminiClient.summarizeArticle(card.title, card.scamType) }
        summary = result ?: ArticleSummary(card.oneLine)
        loading = false
    }

    val seedContext = buildString {
        append("The user is reading a scam-awareness article titled \"${card.title}\"")
        if (card.scamType.isNotBlank()) append(" (category: ${card.scamType})")
        append(".\n")
        append(summary?.text?.takeIf { it.isNotBlank() } ?: card.oneLine)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = VSpace.xl),
                verticalArrangement = Arrangement.spacedBy(VSpace.md),
            ) {
                VaartaBackBar(title = null, onBack = onBack)

                // Clean banner — title + scam-type + source label.
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.indigoTint),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(VSpace.xl), verticalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                        if (card.scamType.isNotBlank()) Eyebrow(card.scamType, color = c.indigo)
                        Text(card.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                        if (card.sourceName.isNotBlank()) {
                            Text("Seen in ${card.sourceName}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("VAARTA is reading the latest on this…", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                } else {
                    MarkdownText(summary?.text.orEmpty(), color = c.ink)
                    summary?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
                        Spacer(Modifier.height(VSpace.xs))
                        Eyebrow("Sources")
                        for (s in sources.take(4)) {
                            SourceLink(title = s.title, onClick = { onOpenUrl(s.uri) })
                        }
                    }
                }
                Spacer(Modifier.height(VSpace.sm))
            }

            // Actions — social-good weave: keep learning, warn family, report.
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VSpace.xl, vertical = VSpace.md),
                verticalArrangement = Arrangement.spacedBy(VSpace.sm),
            ) {
                VaartaButton(
                    text = "Ask VAARTA about this",
                    onClick = { onAskAbout(seedContext) },
                    leadingIcon = R.drawable.ic_sparkle,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                VaartaSecondaryButton(
                    text = "Warn my family",
                    onClick = { onShare(warnFamilyText(card, summary)) },
                    leadingIcon = R.drawable.ic_bell,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Shareable plain-text warning built from the topic + summary, with the 1930 call-to-action. */
private fun warnFamilyText(card: AwarenessCard, summary: ArticleSummary?): String = buildString {
    append("⚠️ Scam alert from VAARTA: ${card.title}\n\n")
    append(summary?.text?.takeIf { it.isNotBlank() } ?: card.oneLine)
    append("\n\nIf this happens to you: stay calm, never pay or share an OTP/PIN, hang up, and report to 1930 or cybercrime.gov.in.")
}
