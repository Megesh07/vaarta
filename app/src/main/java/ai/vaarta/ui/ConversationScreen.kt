package ai.vaarta.ui

import ai.vaarta.ChatThread
import ai.vaarta.StatusBanner
import ai.vaarta.ai.ChatAttachment
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.ui.theme.VaartaTheme
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A free-form "Ask VAARTA" conversation (v2, spec §6.5) — a ChatGPT-style chat with a multimodal
 * composer: type, speak (🎤 device speech-to-text), or attach a screenshot (🖼️) or a call clip (🎧).
 * Full-screen sub-screen: header + Back, the shared [ChatThread], pending-attachment chips, and the
 * composer. The call/recording context header + Download land in a later step.
 */
@Composable
fun ConversationScreen(
    vm: ConversationViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val turns by vm.turns.collectAsState()
    val sending by vm.sending.collectAsState()
    val header by vm.header.collectAsState()
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    val scroll = rememberScrollState()

    LaunchedEffect(turns.size, sending) { scroll.animateScrollTo(scroll.maxValue) }

    // Reads a picked file's bytes off the main thread, then adds it as a pending attachment.
    fun attach(uri: Uri?, label: String, fallbackMime: String) {
        if (uri == null) return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            } ?: return@launch
            val mime = ctx.contentResolver.getType(uri) ?: fallbackMime
            pending = pending + ChatAttachment(mime, bytes, label)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attach(uri, "📷 Photo", "image/jpeg")
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attach(uri, "🎧 Audio clip", "audio/mpeg")
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) input = if (input.isBlank()) said else "$input $said"
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question")
        }
        runCatching { voice.launch(intent) }
    }

    fun submit() {
        vm.send(input, pending)
        input = ""
        pending = emptyList()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹ Back", fontSize = 15.sp, color = c.indigo, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(if (header != null) "About this call" else "Ask VAARTA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                header?.let { h ->
                    Spacer(Modifier.padding(top = 4.dp))
                    StatusBanner(h.level, h.score, reassure = false, aiRaised = false)
                    h.scamType?.let {
                        Text("🌐  $it", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.indigoInk)
                    }
                    Text(
                        "⬇  Download transcript",
                        fontSize = 13.sp,
                        color = c.indigo,
                        modifier = Modifier.clickable { onShare(vm.transcriptText()) }.padding(vertical = 4.dp),
                    )
                }
                if (turns.isEmpty() && header == null) {
                    Spacer(Modifier.padding(top = 24.dp))
                    Text("🛡️", fontSize = 40.sp)
                    Spacer(Modifier.padding(top = 8.dp))
                    Text("Ask me anything about a suspicious call or message", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        "Type, tap 🎤 to speak, or attach a screenshot (🖼️) or a call recording (🎧). " +
                            "I'll tell you if it's a scam and what to do — in plain language.",
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

            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Pending attachment chips (removable).
                if (pending.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        pending.forEach { a ->
                            Surface(color = c.indigoTint, shape = RoundedCornerShape(50)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.label, fontSize = 12.sp, color = c.indigoInk)
                                    Text(
                                        "  ✕",
                                        fontSize = 12.sp,
                                        color = c.indigoInk,
                                        modifier = Modifier.clickable { pending = pending - a },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎤", fontSize = 22.sp, modifier = Modifier.clickable { startVoice() }.padding(4.dp))
                    Text("🖼️", fontSize = 22.sp, modifier = Modifier.clickable { imagePicker.launch("image/*") }.padding(4.dp))
                    Text("🎧", fontSize = 22.sp, modifier = Modifier.clickable { audioPicker.launch("audio/*") }.padding(4.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type your question…") },
                        maxLines = 4,
                    )
                    Button(
                        onClick = { submit() },
                        enabled = (input.isNotBlank() || pending.isNotEmpty()) && !sending,
                        colors = ButtonDefaults.buttonColors(containerColor = c.indigo),
                    ) { Text("Send") }
                }
            }
        }
    }
}
