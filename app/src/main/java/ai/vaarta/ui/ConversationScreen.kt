package ai.vaarta.ui

import ai.vaarta.ChatThread
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A free-form "Ask VAARTA" conversation (v2, spec §6.5) — a ChatGPT-style chat. Full-screen sub-screen:
 * header + Back, the shared [ChatThread], and a bottom text composer. Multimodal input (voice / image /
 * audio) and the call/recording context header are Phase 3; this is the text-only heart.
 */
@Composable
fun ConversationScreen(
    vm: ConversationViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val turns by vm.turns.collectAsState()
    val sending by vm.sending.collectAsState()
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Keep the newest turn in view as the thread grows / while VAARTA is answering.
    LaunchedEffect(turns.size, sending) { scroll.animateScrollTo(scroll.maxValue) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹ Back",
                    fontSize = 15.sp,
                    color = c.indigo,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text("Ask VAARTA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (turns.isEmpty()) {
                    Spacer(Modifier.padding(top = 24.dp))
                    Text("🛡️", fontSize = 40.sp)
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("Ask me anything about a suspicious call or message", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        "Paste what they said, or ask “is this a scam?”, “what do I do?”, or " +
                            "“how do I report it?”. I'll explain in plain language.",
                        fontSize = 14.sp,
                        color = c.muted,
                    )
                } else {
                    ChatThread(turns, onOpenUrl)
                }
                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("VAARTA is thinking…", fontSize = 13.sp, color = c.muted)
                    }
                }
                Spacer(Modifier.padding(bottom = 4.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding().imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type your question…") },
                    maxLines = 4,
                )
                Button(
                    onClick = { vm.send(input); input = "" },
                    enabled = input.isNotBlank() && !sending,
                    colors = ButtonDefaults.buttonColors(containerColor = c.indigo),
                ) { Text("Send") }
            }
        }
    }
}
