package ai.vaarta

import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.Source
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.riskColor
import ai.vaarta.ui.theme.stateLabel
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(headline, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!reassure) {
                Text("Risk $score / 100", color = Color.White, fontSize = 15.sp)
                if (aiRaised) {
                    Spacer(Modifier.height(4.dp))
                    Text("⚠  Flagged from live web intelligence", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Web-grounded scam-variant identification with tappable cited sources. Only shown when source-backed. */
@Composable
internal fun ScamIdCard(scamType: String, sources: List<Source>, onOpenUrl: (String) -> Unit) {
    val c = VaartaTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = c.indigoTint)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("IDENTIFIED FROM THE LIVE WEB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.indigoInk)
            Spacer(Modifier.height(3.dp))
            Text(scamType, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.indigoInk)
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Matches ${sources.size} recent report${if (sources.size == 1) "" else "s"}:", fontSize = 12.sp, color = c.indigoInk)
                for (s in sources.take(3)) {
                    Text(
                        "🔗 ${s.title}",
                        fontSize = 12.sp,
                        color = c.verify,
                        modifier = Modifier.padding(top = 3.dp).clickable { onOpenUrl(s.uri) },
                    )
                }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text("Caller", fontSize = 11.sp, color = c.muted)
                Text(text, fontSize = 15.sp, color = c.ink)
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
                Text("You said", fontSize = 11.sp, color = c.indigoInk)
                Text(text, fontSize = 15.sp, color = c.ink)
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
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.scamType != null) {
                    Text("🌐  ${item.scamType}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.indigoInk)
                    for (s in item.sources.take(3)) {
                        Text(
                            "🔗 ${s.title}",
                            fontSize = 11.sp,
                            color = c.verify,
                            modifier = Modifier.clickable { onOpenUrl(s.uri) },
                        )
                    }
                }
                if (item.warning.isNotBlank()) {
                    Text("⚠️  ${item.warning}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.indigoInk)
                }
                // A recorded-call verdict (Phase 4D) has no live replies — show the header + chips only when present.
                if (item.replies.isNotEmpty()) {
                    Text("SAY THIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.indigo)
                    item.replies.forEachIndexed { i, reply -> ReplyLine(reply, primary = i == 0) }
                }
            }
        }
    }
}

/** A free-form VAARTA chat answer — left bubble, "VAARTA" label, plain prose + tappable sources. */
@Composable
internal fun AssistantBubble(item: ChatItem.Assistant, onOpenUrl: (String) -> Unit) {
    val c = VaartaTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Surface(
            color = c.callerBubble,
            shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 3.dp),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🛡️  VAARTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.indigoInk)
                Text(item.text, fontSize = 15.sp, color = c.ink)
                if (item.sources.isNotEmpty()) {
                    Text("Sources:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.muted)
                    for (s in item.sources.take(3)) {
                        Text(
                            "🔗 ${s.title}",
                            fontSize = 12.sp,
                            color = c.verify,
                            modifier = Modifier.clickable { onOpenUrl(s.uri) },
                        )
                    }
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
            Text(style.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = style.accent)
            Spacer(Modifier.height(2.dp))
            Text(
                "❝ ${reply.text} ❞",
                fontSize = if (primary) 16.sp else 13.sp,
                fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
                color = style.ink,
            )
        }
    }
}
