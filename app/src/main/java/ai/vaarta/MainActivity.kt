package ai.vaarta

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.data.db.CallSessionEntity
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.Source
import ai.vaarta.export.PdfExporter
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.history.SessionDetail
import ai.vaarta.recording.AudioAnalyzerViewModel
import ai.vaarta.ui.RiskHero
import ai.vaarta.ui.theme.VaartaTheme
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: SessionViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val analyzerVm: AudioAnalyzerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VaartaTheme {
                VaartaApp(vm, historyVm, analyzerVm, onShare = ::shareText, onExportPdf = ::exportAndSharePdf, onOpenUrl = ::openUrl)
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

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** PdfExporter needs a Context, so PDF generation happens here, not in the ViewModel. */
    private fun exportAndSharePdf(draft: ComplaintDraft) {
        val file = PdfExporter.export(this, draft)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share complaint PDF"))
    }
}

/** Screen the app is showing. The live copilot is home; history + detail are one tap away (Phase 4B).
 *  A lightweight in-Activity switch, not a nav library — the full hub IA lands with the overlay (4C). */
private sealed interface Screen {
    data object Live : Screen
    data object History : Screen
    data object Detail : Screen
    data object Analyze : Screen
}

/** Top-level host: switches between the live copilot, the saved-history list, a read-only detail, and
 *  the recorded-call analyzer (Phase 4D). */
@Composable
fun VaartaApp(
    vm: SessionViewModel,
    historyVm: HistoryViewModel,
    analyzerVm: AudioAnalyzerViewModel,
    onShare: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Live) }
    when (screen) {
        Screen.Live -> VaartaScreen(
            vm = vm,
            historyVm = historyVm,
            analyzerVm = analyzerVm,
            onOpenHistory = { screen = Screen.History },
            onOpenAnalyze = { screen = Screen.Analyze },
            onShare = onShare,
            onExportPdf = onExportPdf,
            onOpenUrl = onOpenUrl,
        )
        Screen.History -> HistoryScreen(
            historyVm = historyVm,
            onBack = { screen = Screen.Live },
            onOpen = { id -> historyVm.openDetail(id); screen = Screen.Detail },
        )
        Screen.Detail -> DetailScreen(
            historyVm = historyVm,
            onBack = { historyVm.closeDetail(); screen = Screen.History },
            onOpenUrl = onOpenUrl,
        )
        Screen.Analyze -> AnalyzeScreen(
            analyzerVm = analyzerVm,
            historyVm = historyVm,
            onBack = { analyzerVm.reset(); screen = Screen.Live },
            onShare = onShare,
            onOpenUrl = onOpenUrl,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaartaScreen(
    vm: SessionViewModel,
    historyVm: HistoryViewModel,
    analyzerVm: AudioAnalyzerViewModel,
    onOpenHistory: () -> Unit,
    onOpenAnalyze: () -> Unit,
    onShare: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val state by vm.session.state.collectAsState()
    val displayedLevel by vm.session.displayedLevel.collectAsState()
    val aiRaised by vm.session.aiRaised.collectAsState()
    val reassure by vm.session.reassure.collectAsState()
    val scamType by vm.session.scamType.collectAsState()
    val scamSources by vm.session.scamSources.collectAsState()
    val tapped by vm.session.tapped.collectAsState()
    val complaint by vm.session.complaint.collectAsState()
    val complaintDraft by vm.session.complaintDraft.collectAsState()
    val question by vm.session.currentQuestion.collectAsState()
    val aiEnabled by vm.session.aiEnabled.collectAsState()
    val aiSuggestion by vm.session.aiSuggestion.collectAsState()
    val liveStatus by vm.session.liveStatus.collectAsState()
    val chat by vm.session.chat.collectAsState()
    val scroll = rememberScrollState()

    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.session.startLiveListening()
    }

    // Recorded-call analyzer (Phase 4D): pick any audio clip → transcribe + classify → verdict screen.
    // GetContent needs no storage permission (the picker grants a scoped read on the chosen file only).
    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { analyzerVm.analyze(uri); onOpenAnalyze() }
    }

    // --- Floating overlay launch (Phase 4C): grant "draw over other apps" once, then RECORD_AUDIO,
    // then start the foreground service and drop the app to the background so the bubble floats over
    // the dialer. Each permission hop resumes the flow on its result — no mutual forward references. ---
    fun startFloatingService() {
        OverlayService.start(context)
        (context as? Activity)?.moveTaskToBack(true) // reveal the overlay over whatever's underneath
    }
    val floatingMicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startFloatingService()
    }
    val overlayPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) {
            val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (mic) startFloatingService() else floatingMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun launchFloating() {
        if (!Settings.canDrawOverlays(context)) {
            overlayPermLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
            return
        }
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (mic) startFloatingService() else floatingMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // No sound during a call (MOBILE_UX_SPEC §6 — it would leak to the scammer); haptic marks new turns.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("VAARTA", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (liveStatus != null) {
                    Text("● Live: $liveStatus", color = VaartaTheme.colors.indigo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                } else {
                    Text(
                        "🕘 History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaartaTheme.colors.indigo,
                        modifier = Modifier.clickable(onClick = onOpenHistory),
                    )
                }
            }

            RiskHero(
                level = displayedLevel,
                score = state.score,
                reassure = reassure,
                aiRaised = aiRaised,
                detectedStages = state.topSignals.map { it.stage },
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (scamType != null) {
                ScamIdCard(scamType = scamType!!, sources = scamSources, onOpenUrl = onOpenUrl)
            }

            // The live WhatsApp-style thread: caller (left) · you-said (right) · coach "say this"
            // (right, highlighted). Chronological, newest at the bottom. When empty and nothing has
            // been coached yet, fall back to the single deterministic verification question.
            if (chat.isNotEmpty()) {
                ChatThread(chat)
            } else if (question != null) {
                QuestionCard(text = question!!, onCycle = { vm.session.cycleQuestion() })
            } else if (aiEnabled && aiSuggestion != null) {
                CoachBubble(ChatItem.Coach(warning = "", replies = listOf(Reply(aiSuggestion!!, ReplyKind.VERIFY))), onOpenUrl)
            }

            // Live controls.
            if (liveStatus == null) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) vm.session.startLiveListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaartaTheme.colors.indigo),
                ) { Text("🎙  Start live protection", fontSize = 16.sp) }
                // The real in-call mode: float over the dialer as a bubble (Phase 4C).
                OutlinedButton(
                    onClick = { launchFloating() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🪟  Use as a floating window (during a call)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.session.runDemoCall() }) { Text("▶  Try a demo") }
                    OutlinedButton(onClick = { vm.session.reset() }) { Text("Reset") }
                }
                // Analyze a recorded call after the fact (Phase 4D) — only when the AI layer is present.
                if (vm.session.aiConfigured) {
                    OutlinedButton(
                        onClick = { recordingPicker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🎧  Analyze a recorded call") }
                }
                // Explicit opt-in to persist (ADR-0004): only after a call, only on the user's tap.
                if (chat.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            historyVm.save(SessionSource.LIVE, state.score, displayedLevel.name, scamType, chat) {
                                Toast.makeText(context, "Saved to history", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("💾  Save this call to history") }
                }
                if (vm.session.aiConfigured) {
                    AiConsentRow(enabled = aiEnabled, onToggle = { vm.session.setAiEnabled(it) })
                }
            } else {
                Text("🔊  Put the call on speaker so VAARTA can hear the caller.", fontSize = 13.sp, color = VaartaTheme.colors.muted)
                OutlinedButton(onClick = { vm.session.stopLiveListening() }, modifier = Modifier.fillMaxWidth()) {
                    Text("■  Stop protection")
                }
            }

            if (displayedLevel.ordinal >= RiskLevel.HIGH_RISK.ordinal && !reassure) {
                Button(
                    onClick = {
                        onShare("VAARTA alert: I may be on a scam call right now. Please call me back. (${levelText(displayedLevel)})")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaartaTheme.colors.scam),
                ) { Text("🔔  Alert my family", fontSize = 16.sp) }
                Text("No agency arrests anyone over a phone or video call.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            // Manual Mode — always reachable (P0 peer), demoted into its own section.
            HorizontalDivider()
            Text("Manual mode — tap what you hear", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label) in vm.session.cues) {
                    FilterChip(selected = id in tapped, onClick = { vm.session.tapCue(id) }, label = { Text(label) })
                }
            }

            HorizontalDivider()
            OutlinedButton(onClick = { vm.session.generateComplaint() }) { Text("📝  Generate complaint draft") }
            complaint?.let { text ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(text, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onShare(text) }) { Text("Share as text") }
                            complaintDraft?.let { draft -> OutlinedButton(onClick = { onExportPdf(draft) }) { Text("Export PDF") } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** MOBILE_UX_SPEC §3.2 — deterministic verification question fallback; tap cycles. */
@Composable
private fun QuestionCard(text: String, onCycle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCycle),
        colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.indigoTint),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("ASK THEM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VaartaTheme.colors.muted)
            Spacer(Modifier.height(4.dp))
            Text("❝ $text ❞", fontSize = 16.sp, color = VaartaTheme.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text("⟳ tap for another question", fontSize = 11.sp, color = VaartaTheme.colors.muted)
        }
    }
}

/** Opt-in consent for the cloud AI layer (ADR-0002/0003) — OFF by default, honest about what it does. */
@Composable
private fun AiConsentRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.indigoTint)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🤖  AI live coach", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Opt-in. Sends the caller's words to Google (and searches the web for current scams) " +
                        "to coach your reply. Off = fully on-device. Never replaces the safe question.",
                    fontSize = 11.sp,
                    color = VaartaTheme.colors.muted,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// --- Phase 4B: saved history (encrypted at rest, ADR-0004) ---

private val historyDateFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

private fun levelFromName(name: String): RiskLevel =
    runCatching { RiskLevel.valueOf(name) }.getOrDefault(RiskLevel.OBSERVING)

/** The saved-history list — newest first. Tap a row to open the read-only thread; retention + a
 *  delete-all control sit at the top so the user stays in charge of what's stored (ADR-0004). */
@Composable
private fun HistoryScreen(
    historyVm: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val sessions by historyVm.sessions.collectAsState()
    val retentionDays by historyVm.retentionDays.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹ Back", fontSize = 15.sp, color = VaartaTheme.colors.indigo, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.weight(1f))
                Text("Saved calls", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text("Stored only on this phone, encrypted. Nothing is uploaded.", fontSize = 12.sp, color = VaartaTheme.colors.muted)

            RetentionRow(retentionDays = retentionDays, onSet = { historyVm.setRetentionDays(it) })

            if (sessions.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text("No saved calls yet.", fontSize = 15.sp, color = VaartaTheme.colors.muted, modifier = Modifier.fillMaxWidth())
                Text(
                    "After a call, tap “Save this call to history” to keep the thread here.",
                    fontSize = 13.sp, color = VaartaTheme.colors.muted, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                if (sessions.size > 1) {
                    OutlinedButton(
                        onClick = { historyVm.deleteAll() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VaartaTheme.colors.scam),
                    ) { Text("Delete all") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(sessions, key = { it.id }) { session ->
                        HistoryRow(session = session, onOpen = { onOpen(session.id) }, onDelete = { historyVm.delete(session.id) })
                    }
                }
            }
        }
    }
}

/** One saved-call row: risk dot + level + scam-ID + when. */
@Composable
private fun HistoryRow(session: CallSessionEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val level = levelFromName(session.finalLevel)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = levelColor(level), shape = RoundedCornerShape(50), modifier = Modifier.size(12.dp)) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(levelText(level), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = levelColor(level))
                session.scamType?.let { Text(it, fontSize = 12.sp, color = VaartaTheme.colors.muted) }
                Text(historyDateFmt.format(Date(session.startedAtMs)), fontSize = 11.sp, color = VaartaTheme.colors.muted)
            }
            Text("✕", fontSize = 16.sp, color = VaartaTheme.colors.muted, modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
        }
    }
}

/** Retention control — keep forever (default) or auto-delete after N days (user-controlled, ADR-0004). */
@Composable
private fun RetentionRow(retentionDays: Int, onSet: (Int) -> Unit) {
    val options = listOf(0 to "Keep", 7 to "7 days", 30 to "30 days")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Auto-delete:", fontSize = 13.sp, color = VaartaTheme.colors.muted)
        for ((days, label) in options) {
            FilterChip(selected = retentionDays == days, onClick = { onSet(days) }, label = { Text(label) })
        }
    }
}

/** Read-only replay of a saved call: verdict header + the same WhatsApp-style thread + delete. */
@Composable
private fun DetailScreen(
    historyVm: HistoryViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val detail by historyVm.detail.collectAsState()
    val scroll = rememberScrollState()
    val d = detail

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹ Back", fontSize = 15.sp, color = VaartaTheme.colors.indigo, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.weight(1f))
                if (d != null) {
                    Text("✕ Delete", fontSize = 14.sp, color = VaartaTheme.colors.scam, modifier = Modifier.clickable {
                        historyVm.delete(d.id); onBack()
                    })
                }
            }
            if (d == null) {
                Text("Loading…", color = VaartaTheme.colors.muted)
            } else {
                VerdictHeader(d)
                if (d.chat.isEmpty()) {
                    Text("This call has no saved turns.", color = VaartaTheme.colors.muted)
                } else {
                    ChatThread(d.chat, onOpenUrl)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- Phase 4D: recorded-call analyzer screen ---

/**
 * Analyze a recorded call after the fact (Phase 4D, ADR-0003): a picked clip is transcribed +
 * classified by the AI, replayed through the deterministic engine, and shown with the SAME verdict
 * banner + WhatsApp-style thread as a live call — then optionally saved to history as a RECORDING.
 * Fails closed: a friendly message on any error, never a fabricated verdict.
 */
@Composable
private fun AnalyzeScreen(
    analyzerVm: AudioAnalyzerViewModel,
    historyVm: HistoryViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val state by analyzerVm.state.collectAsState()
    val scroll = rememberScrollState()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("‹ Back", fontSize = 15.sp, color = VaartaTheme.colors.indigo, modifier = Modifier.clickable(onClick = onBack))
                Spacer(Modifier.weight(1f))
                Text("Analyze a recording", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            when (val s = state) {
                AudioAnalyzerViewModel.UiState.Idle ->
                    Text("Pick a recording from the home screen to analyze.", fontSize = 14.sp, color = VaartaTheme.colors.muted)

                AudioAnalyzerViewModel.UiState.Running -> {
                    Spacer(Modifier.height(40.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = VaartaTheme.colors.indigo)
                        Text("Transcribing and checking the recording…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("This can take up to a minute for a longer clip.", fontSize = 12.sp, color = VaartaTheme.colors.muted)
                    }
                }

                is AudioAnalyzerViewModel.UiState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.scamTint)) {
                        Text(s.message, modifier = Modifier.padding(16.dp), fontSize = 14.sp, color = VaartaTheme.colors.scam)
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("‹ Back") }
                }

                is AudioAnalyzerViewModel.UiState.Done -> {
                    val r = s.result
                    StatusBanner(level = r.level, score = r.score, reassure = r.reassure, aiRaised = r.aiRaised)
                    Text(
                        "Analyzed from a recording. The risk score is computed on-device from the transcript; " +
                            "the AI transcribed and helped classify it.",
                        fontSize = 11.sp, color = VaartaTheme.colors.muted,
                    )
                    if (r.chat.isNotEmpty()) ChatThread(r.chat, onOpenUrl)

                    if (r.level.ordinal >= RiskLevel.HIGH_RISK.ordinal && !r.reassure) {
                        Button(
                            onClick = { onShare("VAARTA: I analyzed a call recording and it looks like a scam (${levelText(r.level)}).") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaartaTheme.colors.scam),
                        ) { Text("🔔  Share this warning", fontSize = 16.sp) }
                    }

                    OutlinedButton(
                        onClick = {
                            historyVm.save(SessionSource.RECORDING, r.score, r.level.name, r.scamType, r.chat) {
                                Toast.makeText(context, "Saved to history", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("💾  Save to history") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The saved call's final verdict — the steady risk banner + scam-ID, replayed from storage. */
@Composable
private fun VerdictHeader(d: SessionDetail) {
    val level = levelFromName(d.finalLevel)
    Card(colors = CardDefaults.cardColors(containerColor = levelColor(level))) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(levelText(level), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Risk ${d.finalScore} / 100", color = Color.White, fontSize = 15.sp)
            d.scamType?.let {
                Spacer(Modifier.height(4.dp))
                Text("🌐  $it", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(historyDateFmt.format(Date(d.startedAtMs)), color = Color.White, fontSize = 12.sp)
        }
    }
}
