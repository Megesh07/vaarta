package ai.vaarta

import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.RiskState
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VaartaScreen(vm, onShare = ::shareText)
            }
        }
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share via"))
    }
}

private fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.OBSERVING -> Color(0xFF475569)
    RiskLevel.CAUTION -> Color(0xFFF59E0B)
    RiskLevel.HIGH_RISK -> Color(0xFFEA580C)
    RiskLevel.SCAM_PATTERN -> Color(0xFFDC2626)
}

private fun levelText(level: RiskLevel): String = when (level) {
    RiskLevel.OBSERVING -> "Listening & checking…"
    RiskLevel.CAUTION -> "Some warning signs"
    RiskLevel.HIGH_RISK -> "Strong scam signs"
    RiskLevel.SCAM_PATTERN -> "This matches a known scam"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaartaScreen(vm: SessionViewModel, onShare: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val tapped by vm.tapped.collectAsState()
    val complaint by vm.complaint.collectAsState()
    val question by vm.currentQuestion.collectAsState()
    val scroll = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("VAARTA", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Digital-arrest scam protection — MVP", color = Color.Gray, fontSize = 13.sp)

            RiskCard(state)

            question?.let { q -> QuestionCard(text = q, onCycle = { vm.cycleQuestion() }) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.runDemoCall() }) { Text("▶  Run demo scam call") }
                OutlinedButton(onClick = { vm.reset() }) { Text("Reset") }
            }

            if (state.level.ordinal >= RiskLevel.HIGH_RISK.ordinal) {
                Button(
                    onClick = {
                        onShare("VAARTA alert: I may be on a scam call right now. Please call me back. (${levelText(state.level)})")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                ) { Text("🔔  Alert family") }
                Text(
                    "No agency arrests anyone over a phone or video call.",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            HorizontalDivider()
            Text("Manual Mode — tap what you hear", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label) in vm.cues) {
                    FilterChip(
                        selected = id in tapped,
                        onClick = { vm.tapCue(id) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            Button(onClick = { vm.generateComplaint() }) { Text("📝  Generate complaint draft") }
            complaint?.let { text ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(text, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onShare(text) }) { Text("Share / Export") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** MOBILE_UX_SPEC.md §3.2 — exactly one suggested question visible; tap cycles to the next. */
@Composable
private fun QuestionCard(text: String, onCycle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCycle),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF3F8)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("ASK THEM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            Spacer(Modifier.height(4.dp))
            Text("❝ $text ❞", fontSize = 15.sp, color = Color(0xFF1E293B))
            Spacer(Modifier.height(4.dp))
            Text("⟳ tap for another question", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun RiskCard(state: RiskState) {
    Card(colors = CardDefaults.cardColors(containerColor = levelColor(state.level))) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(levelText(state.level), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Risk score: ${state.score} / 100", color = Color.White, fontSize = 15.sp)
            state.topSignals.firstOrNull()?.explain?.let {
                Spacer(Modifier.height(8.dp))
                Text("Why: $it", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
