package ai.vaarta

import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.Source
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

// --- Design tokens + the shared WhatsApp-style chat view (Phase 4A) ---
//
// These presentational composables are the "ChatView" the product design names as a single reusable
// component (docs/decisions/0003 + the Phase-4 architecture): the in-app live screen, the read-only
// history detail, AND the floating overlay panel (Phase 4C) all render the exact same thread through
// them, so the look can never drift between surfaces. `internal` so every file in :app can reuse them.

internal val REASSURE_GREEN = Color(0xFF2E7D32)

internal fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.OBSERVING -> Color(0xFF475569)
    RiskLevel.CAUTION -> Color(0xFFF59E0B)
    RiskLevel.HIGH_RISK -> Color(0xFFEA580C)
    RiskLevel.SCAM_PATTERN -> Color(0xFFDC2626)
}

internal fun levelText(level: RiskLevel): String = when (level) {
    RiskLevel.OBSERVING -> "Listening & checking…"
    RiskLevel.CAUTION -> "Some warning signs"
    RiskLevel.HIGH_RISK -> "Strong scam signs"
    RiskLevel.SCAM_PATTERN -> "This matches a known scam"
}

private data class ReplyStyle(val label: String, val accent: Color, val tint: Color)

private fun replyStyle(kind: ReplyKind): ReplyStyle = when (kind) {
    ReplyKind.VERIFY -> ReplyStyle("Ask", Color(0xFF1565C0), Color(0xFFE3F0FB))
    ReplyKind.REFUSE -> ReplyStyle("Refuse", Color(0xFFB71C1C), Color(0xFFFBE7E7))
    ReplyKind.EXIT -> ReplyStyle("End the call", REASSURE_GREEN, Color(0xFFE6F3E7))
}

/** The hybrid alert banner (ADR-0003): plain language + score. Green when the AI+rules agree the call
 *  is genuine (reassure); otherwise the deterministic/raised risk level. */
@Composable
internal fun StatusBanner(level: RiskLevel, score: Int, reassure: Boolean, aiRaised: Boolean) {
    val color = if (reassure) REASSURE_GREEN else levelColor(level)
    val headline = if (reassure) "This looks like a genuine call" else levelText(level)
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

/** Web-grounded scam-variant identification with tappable cited sources (ADR-0003). Only shown when
 *  source-backed — never a bare AI claim. */
@Composable
internal fun ScamIdCard(scamType: String, sources: List<Source>, onOpenUrl: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1FB))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("IDENTIFIED FROM THE LIVE WEB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C447C))
            Spacer(Modifier.height(3.dp))
            Text(scamType, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF042C53))
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Matches ${sources.size} recent report${if (sources.size == 1) "" else "s"}:", fontSize = 12.sp, color = Color(0xFF0C447C))
                for (s in sources.take(3)) {
                    Text(
                        "🔗 ${s.title}",
                        fontSize = 12.sp,
                        color = Color(0xFF1565C0),
                        modifier = Modifier.padding(top = 3.dp).clickable { onOpenUrl(s.uri) },
                    )
                }
            }
        }
    }
}

/**
 * The live WhatsApp-style thread (Phase 4A). Chronological, caller left / you-and-coach right. Uses a
 * plain [Column] (the screen is already one vertical scroll — a nested LazyColumn would crash on
 * unbounded height); a real call is a few dozen turns, so no virtualization is needed. Newest is at
 * the bottom; the enclosing scroll auto-follows via the caller's LaunchedEffect on chat size.
 */
@Composable
internal fun ChatThread(items: List<ChatItem>, onOpenUrl: (String) -> Unit = {}) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in items) {
            when (item) {
                is ChatItem.Caller -> CallerBubble(item.text)
                is ChatItem.You -> YouBubble(item.text)
                is ChatItem.Coach -> CoachBubble(item, onOpenUrl)
            }
        }
    }
}

/** Scammer's words — left bubble. */
@Composable
private fun CallerBubble(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFFF1F1F1),
            shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 3.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(11.dp)) {
                Text("Caller", fontSize = 11.sp, color = Color(0xFF757575))
                Text(text, fontSize = 15.sp, color = Color(0xFF1E1E1E))
            }
        }
    }
}

/** The user's own spoken words — right bubble, lighter than a coach suggestion. */
@Composable
private fun YouBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = Color(0xFFF7F5FC),
            shape = RoundedCornerShape(12.dp, 12.dp, 3.dp, 12.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(11.dp)) {
                Text("You said", fontSize = 11.sp, color = Color(0xFF7E57C2))
                Text(text, fontSize = 15.sp, color = Color(0xFF1E1E1E))
            }
        }
    }
}

/**
 * VAARTA's coaching — right side, highlighted. Warning as a small line, then the primary reply
 * ("say this", large) + alternates as chips, and the web-grounded scam-ID with tappable sources when
 * present. Replies are display-only — never auto-played (ADR-0002 S8: audio would leak to the scammer).
 */
@Composable
internal fun CoachBubble(item: ChatItem.Coach, onOpenUrl: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = Color(0xFFEDE7F6),
            shape = RoundedCornerShape(12.dp, 12.dp, 3.dp, 12.dp),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.scamType != null) {
                    Text("🌐  ${item.scamType}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C447C))
                    for (s in item.sources.take(3)) {
                        Text(
                            "🔗 ${s.title}",
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.clickable { onOpenUrl(s.uri) },
                        )
                    }
                }
                if (item.warning.isNotBlank()) {
                    Text("⚠️  ${item.warning}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4527A0))
                }
                // Replies are the live copilot's "say this" coaching; a recorded-call verdict (Phase 4D)
                // has none (the call is already over), so show the header + chips only when present.
                if (item.replies.isNotEmpty()) {
                    Text("SAY THIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4527A0))
                    item.replies.forEachIndexed { i, reply -> ReplyLine(reply, primary = i == 0) }
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
                color = Color(0xFF1E1E1E),
            )
        }
    }
}
