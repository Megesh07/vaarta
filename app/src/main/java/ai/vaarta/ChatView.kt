package ai.vaarta

import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.Source
import ai.vaarta.ui.MarkdownText
import ai.vaarta.ui.VaartaIcon
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.SourceLink
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.riskColor
import ai.vaarta.ui.theme.stateLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// --- The shared WhatsApp-style chat view (design system §6). Every surface — the in-app live screen,
// the read-only history detail, and the floating overlay panel — renders the exact same thread through
// these, so the look can never drift. All colour flows through VaartaTheme, so light/dark is automatic.

/** The risk-ramp fill for a level — one place, from the theme (design system §4). */
@Composable
internal fun levelColor(level: RiskLevel): Color = VaartaTheme.colors.riskColor(level)

/** Plain-language state line (kept for the overlay banner + history verdict). */
internal fun levelText(level: RiskLevel): String = stateLabel(level)

private data class ReplyStyle(val label: String, val accent: Color, val tint: Color, val ink: Color)

@Composable
private fun replyStyle(kind: ReplyKind): ReplyStyle {
    val c = VaartaTheme.colors
    return when (kind) {
        ReplyKind.VERIFY -> ReplyStyle("Ask", c.verify, c.verifyTint, c.verifyInk)
        ReplyKind.REFUSE -> ReplyStyle("Refuse", c.refuse, c.refuseTint, c.refuseInk)
        ReplyKind.EXIT -> ReplyStyle("End the call", c.exit, c.exitTint, c.exitInk)
    }
}

/** The hybrid alert banner (kept for the overlay panel): plain language + score, green when reassured. */
@Composable
internal fun StatusBanner(level: RiskLevel, score: Int, reassure: Boolean, aiRaised: Boolean) {
    val c = VaartaTheme.colors
    val color = if (reassure) c.safe else c.riskColor(level)
    val headline = if (reassure) "This looks like a genuine call" else stateLabel(level)
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(VSpace.xl)) {
            Text(headline, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            if (!reassure) {
                Text("Risk $score / 100", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                if (aiRaised) {
                    Spacer(Modifier.height(VSpace.xs))
                    IconLabel(R.drawable.ic_alert_triangle, "Flagged from live web intelligence", Color.White)
                }
            }
        }
    }
}

/** A small leading-icon + label row, used for inline markers inside bubbles/banners. */
@Composable
private fun IconLabel(icon: Int, text: String, tint: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
        VaartaIcon(icon, contentDescription = null, tint = tint, size = 16.dp)
        Text(
            text,
            style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

/** Web-grounded scam-variant identification with tappable cited sources. Only shown when source-backed. */
@Composable
internal fun ScamIdCard(scamType: String, sources: List<Source>, onOpenUrl: (String) -> Unit) {
    val c = VaartaTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = c.indigoTint)) {
        Column(Modifier.fillMaxWidth().padding(VSpace.md)) {
            Eyebrow("Identified from the live web", color = c.indigoInk)
            Spacer(Modifier.height(VSpace.xs))
            Text(scamType, style = MaterialTheme.typography.titleMedium, color = c.indigoInk)
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(VSpace.xs))
                Text(
                    "Matches ${sources.size} recent report${if (sources.size == 1) "" else "s"}:",
                    style = MaterialTheme.typography.bodySmall, color = c.indigoInk,
                )
                for (s in sources.take(3)) SourceLink(title = s.title, onClick = { onOpenUrl(s.uri) })
            }
        }
    }
}

/**
 * The live WhatsApp-style thread. Chronological, caller left / you-and-coach right. Uses a plain
 * [Column] (the screen is already one vertical scroll); a real call is a few dozen turns, so no
 * virtualization is needed. Newest at the bottom.
 */
@Composable
internal fun ChatThread(items: List<ChatItem>, onOpenUrl: (String) -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VSpace.sm)) {
        for (item in items) {
            when (item) {
                is ChatItem.Caller -> CallerBubble(item.text)
                is ChatItem.You -> YouBubble(item.text)
                is ChatItem.Coach -> CoachBubble(item, onOpenUrl)
                is ChatItem.Assistant -> AssistantBubble(item, onOpenUrl)
            }
        }
    }
}

/** Scammer's words — left bubble. */
@Composable
private fun CallerBubble(text: String) {
    val c = VaartaTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Surface(
            color = c.callerBubble,
            shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 3.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(11.dp)) {
                Text("Caller", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
        }
    }
}

/** The user's own spoken words — right bubble, lighter than a coach suggestion. */
@Composable
private fun YouBubble(text: String) {
    val c = VaartaTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = c.indigoTint,
            shape = RoundedCornerShape(12.dp, 12.dp, 3.dp, 12.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(11.dp)) {
                Text("You said", style = MaterialTheme.typography.labelMedium, color = c.indigoInk)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            }
        }
    }
}

/**
 * VAARTA's coaching — right side, highlighted. Warning as a small line, then the primary reply
 * ("say this", large) + alternates, and the web-grounded scam-ID with tappable sources when present.
 * Replies are display-only — never auto-played (ADR-0002 S8: audio would leak to the scammer).
 */
@Composable
internal fun CoachBubble(item: ChatItem.Coach, onOpenUrl: (String) -> Unit) {
    val c = VaartaTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = c.indigoTint,
            shape = RoundedCornerShape(12.dp, 12.dp, 3.dp, 12.dp),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                if (item.scamType != null) {
                    IconLabel(R.drawable.ic_globe, item.scamType, c.indigoInk, bold = true)
                    for (s in item.sources.take(3)) SourceLink(title = s.title, onClick = { onOpenUrl(s.uri) })
                }
                if (item.warning.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                        VaartaIcon(R.drawable.ic_alert_triangle, contentDescription = null, tint = c.indigoInk, size = 16.dp)
                        MarkdownText(item.warning, color = c.indigoInk)
                    }
                }
                // A recorded-call verdict (Phase 4D) has no live replies — show the header + chips only when present.
                if (item.replies.isNotEmpty()) {
                    Eyebrow("Say this", color = c.indigo)
                    item.replies.forEachIndexed { i, reply -> ReplyLine(reply, primary = i == 0) }
                }
            }
        }
    }
}

/** A free-form VAARTA chat answer — left bubble, "VAARTA" label, markdown prose + tappable sources. */
@Composable
internal fun AssistantBubble(item: ChatItem.Assistant, onOpenUrl: (String) -> Unit) {
    val c = VaartaTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Surface(
            color = c.callerBubble,
            shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 3.dp),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                IconLabel(R.drawable.ic_sparkle, "VAARTA", c.indigoInk, bold = true)
                MarkdownText(item.text, color = c.ink)
                if (item.sources.isNotEmpty()) {
                    Eyebrow("Sources")
                    for (s in item.sources.take(3)) SourceLink(title = s.title, onClick = { onOpenUrl(s.uri) })
                }
            }
        }
    }
}

/** One suggested reply — the primary large, alternates as compact chips. */
@Composable
private fun ReplyLine(reply: Reply, primary: Boolean) {
    val style = replyStyle(reply.kind)
    Surface(color = style.tint, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (primary) 11.dp else 9.dp)) {
            Eyebrow(style.label, color = style.accent)
            Spacer(Modifier.height(2.dp))
            Text(
                reply.text,
                style = if (primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = style.ink,
            )
        }
    }
}
