package ai.vaarta.ui

import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.reasoning.ArticleSummary
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Article summary screen (v2, spec §6.1). Opened from a home feed card: a clean banner (title +
 * scam-type + source label), then a plain-language AI summary of the *real* topic grounded on current
 * web sources — with the cited sources shown and tappable (the honest attribution: we show what
 * grounding actually cited, we don't fabricate a link). Fails closed to the card's one-line if
 * summarization errors. "Ask about this" seeds a NEW conversation; the summary itself is ephemeral
 * (regenerated cheaply on reopen) — only a chat the user engages in is saved.
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
        // Fail closed: if the grounded summary errors, fall back to the card's own one-line.
        summary = result ?: ArticleSummary(card.oneLine)
        loading = false
    }

    // Context handed to "Ask about this" — VAARTA answers the follow-up chat grounded in this topic.
    val seedContext = buildString {
        append("The user is reading a scam-awareness article titled \"${card.title}\"")
        if (card.scamType.isNotBlank()) append(" (category: ${card.scamType})")
        append(".\n")
        append(summary?.text?.takeIf { it.isNotBlank() } ?: card.oneLine)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Back", fontSize = 15.sp, color = c.indigo, modifier = Modifier.clickable(onClick = onBack))
                }

                // Clean banner — title + scam-type + source label.
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.indigoTint),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (card.scamType.isNotBlank()) {
                            Text(card.scamType.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.indigo)
                        }
                        Text(card.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        if (card.sourceName.isNotBlank()) {
                            Text("Seen in ${card.sourceName}", fontSize = 12.sp, color = c.muted)
                        }
                    }
                }

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("VAARTA is reading the latest on this…", fontSize = 13.sp, color = c.muted)
                    }
                } else {
                    Text(summary?.text.orEmpty(), fontSize = 16.sp, color = c.ink)
                    summary?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
                        Spacer(Modifier.height(2.dp))
                        Text("Sources", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.muted)
                        for (s in sources.take(4)) {
                            Text(
                                "🔗 ${s.title}",
                                fontSize = 13.sp,
                                color = c.verify,
                                modifier = Modifier.clickable { onOpenUrl(s.uri) }.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Actions — social-good weave: keep learning, warn family, report.
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAskAbout(seedContext) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.indigo),
                ) { Text("💬  Ask VAARTA about this", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = { onShare(warnFamilyText(card, summary)) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📤  Warn my family") }
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
