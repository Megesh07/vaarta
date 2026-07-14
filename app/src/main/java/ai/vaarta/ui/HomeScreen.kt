package ai.vaarta.ui

import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The clean landing screen (spec §4.1). One visually-dominant PANIC control in the thumb zone, two
 * secondary action cards, and a (not-yet-populated) trending-scams section. No Manual Mode. Strong
 * red is reserved for the panic context only (60/30/10 — red means real danger, never decoration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    aiConfigured: Boolean,
    onStartLive: () -> Unit,
    onAnalyzeRecording: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val scroll = rememberScrollState()
    var showPanic by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Column {
                Text("VAARTA", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text(
                    "Your guardian against phone scams",
                    fontSize = 15.sp,
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
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("🚨", fontSize = 34.sp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "I'm on a scam call right now",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Tap for what to do this second",
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Two calm action cards.
            ActionCard(
                emoji = "🎙️",
                title = "Help me on a call",
                subtitle = "VAARTA listens on speaker and coaches you live",
                onClick = onStartLive,
            )
            if (aiConfigured) {
                ActionCard(
                    emoji = "🎧",
                    title = "Check a recording",
                    subtitle = "Analyze a call you already recorded",
                    onClick = onAnalyzeRecording,
                )
            }

            // Trending scams — real AI-generated feed lands in Phase 4.
            Spacer(Modifier.height(4.dp))
            Text("Trending scams in India", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Card(
                colors = CardDefaults.cardColors(containerColor = c.panel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Live scam-awareness stories will appear here soon — tap any story and VAARTA " +
                        "explains it in plain language, with trusted sources.",
                    Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = c.muted,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPanic) {
        ModalBottomSheet(
            onDismissRequest = { showPanic = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Do this right now", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.scam)
                PanicStep("1", "Don't pay anyone. No real officer or bank asks for money on a call.")
                PanicStep("2", "Never share an OTP, PIN, or password. Ever.")
                PanicStep("3", "Hang up. It is safe to end the call — no one is arrested over a phone call.")
                PanicStep("4", "Call 1930 (the government cyber-crime helpline).")
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onOpenUrl("tel:1930") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.scam),
                ) { Text("📞  Call 1930 now", fontSize = 16.sp) }
                OutlinedButton(
                    onClick = { showPanic = false; onStartLive() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🎙️  Start live help") }
                if (aiConfigured) {
                    OutlinedButton(
                        onClick = { showPanic = false; onAnalyzeRecording() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🎧  Analyze a recording") }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.indigoTint),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(emoji, fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(subtitle, fontSize = 13.sp, color = c.muted)
            }
            Text("›", fontSize = 24.sp, color = c.indigo)
        }
    }
}

@Composable
private fun PanicStep(number: String, text: String) {
    val c = VaartaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = c.scamTint, shape = RoundedCornerShape(50), modifier = Modifier.height(28.dp)) {
            Text(
                number,
                Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
                color = c.scam,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
        Text(text, fontSize = 16.sp, color = c.ink, modifier = Modifier.weight(1f))
    }
}
