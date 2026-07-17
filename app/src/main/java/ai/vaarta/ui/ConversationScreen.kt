package ai.vaarta.ui

import ai.vaarta.ChatThread
import ai.vaarta.R
import ai.vaarta.StatusBanner
import ai.vaarta.ai.ChatAttachment
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.ui.components.VaartaBackBar
import ai.vaarta.ui.theme.VSpace
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A free-form "Ask VAARTA" conversation (v2, spec §6.5) — a ChatGPT-style chat with a multimodal
 * composer: type, speak (device speech-to-text), or attach a screenshot or a call clip. Full-screen
 * sub-screen: back bar, the shared [ChatThread], pending-attachment chips, and the composer. AI
 * answers render through [MarkdownText] inside the thread, so no raw markup ever shows.
 */
@Composable
fun ConversationScreen(
    vm: ConversationViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.activity.compose.BackHandler(onBack = onBack) // system back == the back bar (spec §8.2)
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
            VaartaBackBar(title = if (header != null) "About this call" else "Ask VAARTA", onBack = onBack)

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = VSpace.lg),
                verticalArrangement = Arrangement.spacedBy(VSpace.md),
            ) {
                header?.let { h ->
                    Spacer(Modifier.height(VSpace.xs))
                    StatusBanner(h.level, h.score, reassure = false, aiRaised = false)
                    h.scamType?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                            VaartaIcon(R.drawable.ic_globe, contentDescription = null, tint = c.indigoInk, size = 16.dp)
                            Text(it, style = MaterialTheme.typography.labelLarge, color = c.indigoInk)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
                        modifier = Modifier.clickable { onShare(vm.transcriptText()) }.padding(vertical = VSpace.xs),
                    ) {
                        VaartaIcon(R.drawable.ic_download, contentDescription = null, tint = c.indigo, size = 16.dp)
                        Text("Download transcript", style = MaterialTheme.typography.bodySmall, color = c.indigo)
                    }
                }
                if (turns.isEmpty() && header == null) {
                    Spacer(Modifier.height(VSpace.xxl))
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(VSpace.md),
                    ) {
                        VaartaIcon(R.drawable.ic_nav_shield, contentDescription = null, tint = c.faint, size = 40.dp)
                        Text(
                            "Ask me anything about a suspicious call or message",
                            style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center,
                        )
                        Text(
                            "Type, speak, or attach a screenshot or a call recording. I'll tell you if it's a " +
                                "scam and what to do — in plain language.",
                            style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    ChatThread(turns, onOpenUrl)
                }
                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text("VAARTA is thinking…", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                Spacer(Modifier.height(VSpace.xs))
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = VSpace.md, vertical = VSpace.sm)) {
                // Pending attachment chips (removable).
                if (pending.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm), modifier = Modifier.padding(bottom = VSpace.xs)) {
                        pending.forEach { a ->
                            Surface(color = c.indigoTint, shape = RoundedCornerShape(50)) {
                                Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                                    Text(a.label, style = MaterialTheme.typography.bodySmall, color = c.indigoInk)
                                    VaartaIcon(
                                        R.drawable.ic_close, contentDescription = "Remove", tint = c.indigoInk, size = 14.dp,
                                        modifier = Modifier.clickable { pending = pending - a },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                    VaartaIcon(R.drawable.ic_mic, contentDescription = "Speak", tint = c.muted, size = 24.dp, modifier = Modifier.clickable { startVoice() }.padding(4.dp))
                    VaartaIcon(R.drawable.ic_image, contentDescription = "Attach image", tint = c.muted, size = 24.dp, modifier = Modifier.clickable { imagePicker.launch("image/*") }.padding(4.dp))
                    VaartaIcon(R.drawable.ic_headphones, contentDescription = "Attach recording", tint = c.muted, size = 24.dp, modifier = Modifier.clickable { audioPicker.launch("audio/*") }.padding(4.dp))
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
