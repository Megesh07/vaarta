package ai.vaarta.ui

import ai.vaarta.ChatThread
import ai.vaarta.R
import ai.vaarta.StatusBanner
import ai.vaarta.ai.ChatAttachment
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.ui.components.VaartaBackBar
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.vaartaPressable
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CHAT_STARTERS = listOf(R.string.chat_starter_1, R.string.chat_starter_2, R.string.chat_starter_3)

/**
 * A free-form "Ask VAARTA" conversation (v2, spec §6.7) — a ChatGPT-style chat with a multimodal
 * composer: type, speak (device speech-to-text), or attach a screenshot or a call clip. Full-screen
 * sub-screen: back bar, the shared [ChatThread], pending-attachment chips, and the composer. AI
 * answers render through [MarkdownText] inside the thread, so no raw markup ever shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit,
    onShareGeneric: (String) -> Unit,
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
    var showAttachSheet by remember { mutableStateOf(false) }
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

    val pendingPhotoLabel = stringResource(R.string.chat_pending_photo)
    val pendingAudioLabel = stringResource(R.string.chat_pending_audio)
    val speechPrompt = stringResource(R.string.chat_speech_prompt)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attach(uri, pendingPhotoLabel, "image/jpeg")
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attach(uri, pendingAudioLabel, "audio/mpeg")
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.current().speechLocaleTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
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
            VaartaBackBar(title = if (header != null) stringResource(R.string.chat_about_call_title) else stringResource(R.string.chat_title), onBack = onBack)

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
                        modifier = Modifier.vaartaPressable({ onShareGeneric(vm.transcriptText()) }).padding(vertical = VSpace.xs),
                    ) {
                        VaartaIcon(R.drawable.ic_download, contentDescription = null, tint = c.indigo, size = 16.dp)
                        Text(stringResource(R.string.chat_download_transcript), style = MaterialTheme.typography.bodySmall, color = c.indigo)
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
                            stringResource(R.string.chat_empty_title),
                            style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(VSpace.xs))
                        for (starter in CHAT_STARTERS) {
                            StarterChip(stringResource(starter), onClick = { vm.send(ctx.getString(starter), emptyList()) })
                        }
                    }
                } else {
                    ChatThread(turns, onOpenUrl)
                }
                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.chat_thinking), style = MaterialTheme.typography.bodySmall, color = c.muted)
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
                                    Text(
                                        a.label, style = MaterialTheme.typography.bodySmall, color = c.indigoInk,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp),
                                    )
                                    VaartaIcon(
                                        R.drawable.ic_close, contentDescription = stringResource(R.string.chat_remove_a11y), tint = c.indigoInk, size = 14.dp,
                                        modifier = Modifier.vaartaPressable({ pending = pending - a }),
                                    )
                                }
                            }
                        }
                    }
                }
                // The pill composer (redesign spec §6.7): one rounded field with mic + attach as
                // trailing icons inside it, and a circular indigo send button outside — 3 always-visible
                // gray icons collapse to 1 ("+", which opens the attach sheet).
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                    Surface(color = c.panel, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.chat_composer_hint)) },
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                            VaartaIcon(
                                R.drawable.ic_mic, contentDescription = stringResource(R.string.chat_mic_a11y), tint = c.muted, size = 22.dp,
                                modifier = Modifier.vaartaPressable({ startVoice() }).padding(8.dp),
                            )
                            VaartaIcon(
                                R.drawable.ic_plus, contentDescription = stringResource(R.string.chat_attach_a11y), tint = c.muted, size = 22.dp,
                                modifier = Modifier.vaartaPressable({ showAttachSheet = true }).padding(end = VSpace.sm).padding(8.dp),
                            )
                        }
                    }
                    Surface(
                        color = if ((input.isNotBlank() || pending.isNotEmpty()) && !sending) c.indigo else c.track,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp).vaartaPressable(
                            onClick = { submit() },
                            enabled = (input.isNotBlank() || pending.isNotEmpty()) && !sending,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            VaartaIcon(R.drawable.ic_send, contentDescription = stringResource(R.string.chat_send_a11y), tint = Color.White, size = 20.dp)
                        }
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl)) {
                Text(stringResource(R.string.chat_attach_sheet_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(VSpace.sm))
                AttachRow(R.drawable.ic_image, stringResource(R.string.chat_attach_photo)) {
                    showAttachSheet = false; imagePicker.launch("image/*")
                }
                AttachRow(R.drawable.ic_headphones, stringResource(R.string.chat_attach_audio)) {
                    showAttachSheet = false; audioPicker.launch("audio/*")
                }
            }
        }
    }
}

/** One India-specific prompt starter (spec §6.7) — tapping sends it immediately, no typing needed. */
@Composable
private fun StarterChip(text: String, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Surface(
        color = c.indigoTint, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().vaartaPressable(onClick),
    ) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = c.indigoInk,
            modifier = Modifier.padding(horizontal = VSpace.lg, vertical = VSpace.md),
        )
    }
}

/** One row inside the attach bottom sheet. */
@Composable
private fun AttachRow(icon: Int, text: String, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Row(
        Modifier.fillMaxWidth().vaartaPressable(onClick).padding(vertical = VSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.md),
    ) {
        VaartaIcon(icon, contentDescription = null, tint = c.indigo, size = 22.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}
