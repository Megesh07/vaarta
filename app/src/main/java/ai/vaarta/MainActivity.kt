package ai.vaarta

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.data.db.CallSessionEntity
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.relativeTimeLabel
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.core.reasoning.Source
import ai.vaarta.feed.AwarenessViewModel
import ai.vaarta.export.PdfExporter
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.recording.AudioAnalyzerViewModel
import ai.vaarta.ui.RiskHero
import ai.vaarta.ui.VaartaIcon
import ai.vaarta.ui.VaartaNav
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaBackBar
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SessionViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val analyzerVm: AudioAnalyzerViewModel by viewModels()
    private val conversationVm: ConversationViewModel by viewModels()
    private val awarenessVm: AwarenessViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // system-bar icons follow light/dark correctly (spec §8.1)
        setContent {
            VaartaTheme {
                VaartaNav(vm, historyVm, analyzerVm, conversationVm, awarenessVm, onShare = ::shareText, onExportPdf = ::exportAndSharePdf, onOpenUrl = ::openUrl)
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

@Composable
fun VaartaScreen(
    vm: SessionViewModel,
    historyVm: HistoryViewModel,
    onOpenHistory: () -> Unit,
    onShare: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack) // system back == the header back arrow (spec §8.2)
    val state by vm.session.state.collectAsState()
    val displayedLevel by vm.session.displayedLevel.collectAsState()
    val aiRaised by vm.session.aiRaised.collectAsState()
    val reassure by vm.session.reassure.collectAsState()
    val scamType by vm.session.scamType.collectAsState()
    val scamSources by vm.session.scamSources.collectAsState()
    val question by vm.session.currentQuestion.collectAsState()
    val aiEnabled by vm.session.aiEnabled.collectAsState()
    val aiSuggestion by vm.session.aiSuggestion.collectAsState()
    val aiLoading by vm.session.aiLoading.collectAsState()
    val liveStatus by vm.session.liveStatus.collectAsState()
    val chat by vm.session.chat.collectAsState()
    val scroll = rememberScrollState()

    // Three explicit states (redesign spec §6.3): idle (nothing has happened yet — no fake
    // "Listening & checking 0"), active (a real live call), post-session (a demo or a call just
    // ended — Reset lives here now, not in idle).
    val isIdle = liveStatus == null && chat.isEmpty() && question == null && aiSuggestion == null

    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.session.startLiveListening()
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

    // Auto-save a live call to Conversations when it ends with content (v2 — no manual Save tap needed;
    // starting protection is the consent, and the user can delete any conversation). Demos aren't saved
    // (they never set liveStatus).
    var wasLive by remember { mutableStateOf(false) }
    var savedLive by remember { mutableStateOf(false) }
    LaunchedEffect(liveStatus) {
        if (liveStatus != null) {
            wasLive = true
            savedLive = false
        } else if (wasLive && !savedLive && chat.isNotEmpty()) {
            historyVm.save(SessionSource.LIVE, state.score, displayedLevel.name, scamType, chat) {
                Toast.makeText(context, "Saved to your conversations", Toast.LENGTH_SHORT).show()
            }
            savedLive = true
            wasLive = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                VaartaIcon(
                    R.drawable.ic_arrow_left, contentDescription = "Back", tint = VaartaTheme.colors.ink, size = 24.dp,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = VSpace.md),
                )
                Text(stringResource(R.string.live_title), style = MaterialTheme.typography.titleLarge, color = VaartaTheme.colors.ink)
            }

            RiskHero(
                level = displayedLevel,
                score = state.score,
                reassure = reassure,
                aiRaised = aiRaised,
                detectedStages = state.topSignals.map { it.stage },
                modifier = Modifier.padding(vertical = 8.dp),
                idleLabel = if (isIdle) stringResource(R.string.live_idle_title) else null,
                liveBadge = liveStatus != null,
            )
            if (isIdle) {
                Text(
                    stringResource(R.string.live_idle_caption),
                    style = MaterialTheme.typography.bodyMedium, color = VaartaTheme.colors.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (scamType != null) {
                ScamIdCard(scamType = scamType!!, sources = scamSources, onOpenUrl = onOpenUrl)
            }

            // The live WhatsApp-style thread: caller (left) · you-said (right) · coach "say this"
            // (right, highlighted). Chronological, newest at the bottom. When empty and nothing has
            // been coached yet, fall back to the single deterministic verification question.
            if (chat.isNotEmpty()) {
                ChatThread(chat)
                // Demo/text-mode only: the single-shot AI suggestion arrives after the deterministic
                // thread is built, so it renders as a trailing coach bubble. Never during a live call —
                // there the copilot streams its coaching into [chat] itself and this would duplicate.
                if (liveStatus == null && aiEnabled) {
                    if (aiLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text("AI coach is thinking…", style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
                        }
                    } else {
                        aiSuggestion?.let { s ->
                            CoachBubble(ChatItem.Coach(warning = "", replies = listOf(Reply(s, ReplyKind.VERIFY))), onOpenUrl)
                        }
                    }
                }
            } else if (question != null) {
                QuestionCard(text = question!!, onCycle = { vm.session.cycleQuestion() })
            } else if (aiEnabled && aiSuggestion != null) {
                CoachBubble(ChatItem.Coach(warning = "", replies = listOf(Reply(aiSuggestion!!, ReplyKind.VERIFY))), onOpenUrl)
            }

            // Live controls — bottom-anchored group; three explicit states (redesign spec §6.3).
            when {
                liveStatus != null -> {
                    // Active: the ring + pulsing dot above already say "this is live" — just the exit.
                    Text(
                        stringResource(R.string.live_active_caption),
                        style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted,
                    )
                    VaartaSecondaryButton(
                        text = stringResource(R.string.live_stop),
                        onClick = { vm.session.stopLiveListening() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                !isIdle -> {
                    // Post-session: a demo just played, or a real call just ended. Reset lives here.
                    if (savedLive) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                            VaartaIcon(R.drawable.ic_check, contentDescription = null, tint = VaartaTheme.colors.safe, size = 18.dp)
                            Text(stringResource(R.string.live_saved), style = MaterialTheme.typography.bodyMedium, color = VaartaTheme.colors.safe)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm), modifier = Modifier.fillMaxWidth()) {
                        VaartaSecondaryButton(text = stringResource(R.string.live_done), onClick = onBack, modifier = Modifier.weight(1f))
                        VaartaButton(text = stringResource(R.string.live_start_again), onClick = { vm.session.reset() }, modifier = Modifier.weight(1f))
                    }
                }
                else -> {
                    // Idle: primary Start, secondary Float, a quiet demo link, compact AI-consent row.
                    VaartaButton(
                        text = stringResource(R.string.live_start),
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) vm.session.startLiveListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        leadingIcon = R.drawable.ic_mic,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The real in-call mode: float over the dialer as a bubble (Phase 4C).
                    VaartaSecondaryButton(
                        text = stringResource(R.string.live_float),
                        onClick = { launchFloating() },
                        leadingIcon = R.drawable.ic_pip,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextLinkRow(
                        text = stringResource(R.string.live_watch_demo),
                        onClick = { vm.session.runDemoCall() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.session.aiConfigured) {
                        AiConsentRow(enabled = aiEnabled, onToggle = { vm.session.setAiEnabled(it) })
                    }
                }
            }

            if (displayedLevel.ordinal >= RiskLevel.HIGH_RISK.ordinal && !reassure) {
                VaartaButton(
                    text = "Alert my family",
                    onClick = {
                        onShare("VAARTA alert: I may be on a scam call right now. Please call me back. (${levelText(displayedLevel)})")
                    },
                    leadingIcon = R.drawable.ic_bell,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "No agency arrests anyone over a phone or video call.",
                    style = MaterialTheme.typography.titleMedium, color = VaartaTheme.colors.ink,
                )
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
        Column(Modifier.padding(VSpace.md)) {
            Eyebrow("Ask them")
            Spacer(Modifier.height(VSpace.xs))
            Text(text, style = MaterialTheme.typography.titleMedium, color = VaartaTheme.colors.ink)
            Spacer(Modifier.height(VSpace.xs))
            Text("Tap for another question", style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
        }
    }
}

/**
 * Opt-in consent for the cloud AI layer (ADR-0002/0003) — OFF by default. Compacted to one line +
 * switch (redesign spec §6.3) — the old 5-line paragraph was a major source of "text text text".
 */
@Composable
private fun AiConsentRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.indigoTint)) {
        Row(modifier = Modifier.fillMaxWidth().padding(VSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                VaartaIcon(R.drawable.ic_sparkle, contentDescription = null, tint = VaartaTheme.colors.indigoInk, size = 18.dp)
                Text(
                    stringResource(R.string.live_ai_consent_compact),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaartaTheme.colors.ink,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// --- Phase 4B: saved history (encrypted at rest, ADR-0004) ---

private fun levelFromName(name: String): RiskLevel =
    runCatching { RiskLevel.valueOf(name) }.getOrDefault(RiskLevel.OBSERVING)

/**
 * Conversations v2 (redesign spec §6.6). A clean header (title + count + kebab), an extended
 * "New chat" FAB in the thumb zone, the shared row grammar (single-line title + verdict pill +
 * relative time, chevron only), swipe-to-delete with an Undo snackbar (the actual DB delete is
 * deferred behind a pending set so Undo needs no re-insert), and the retention/Delete-all controls
 * moved into a kebab bottom-sheet so they no longer occupy the prime scroll space. Encryption note
 * shrinks to a lock caption at the list foot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    historyVm: HistoryViewModel,
    onNewChat: () -> Unit,
    onOpen: (CallSessionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val sessions by historyVm.sessions.collectAsState()
    val retentionDays by historyVm.retentionDays.collectAsState()
    val pending = remember { mutableStateListOf<Long>() }
    val visible = sessions.filter { it.id !in pending }
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val thisWeek = visible.filter { it.startedAtMs >= weekAgo }
    val earlier = visible.filter { it.startedAtMs < weekAgo }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val deletedMsg = stringResource(R.string.conv_deleted)
    val undoLabel = stringResource(R.string.conv_undo)

    fun requestDelete(id: Long) {
        pending.add(id)
        scope.launch {
            val result = snackbarHost.showSnackbar(deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                pending.remove(id) // undo: the row is still in the DB, just un-hide it
            } else {
                pending.remove(id)
                historyVm.delete(id) // commit the delete only once the window closed without undo
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = VSpace.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = VSpace.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.conv_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        if (sessions.isNotEmpty()) {
                            Eyebrow(stringResource(R.string.conv_count, sessions.size))
                        }
                    }
                    Surface(
                        color = Color.Transparent, shape = CircleShape,
                        modifier = Modifier.size(44.dp).clickable { showMenu = true },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            VaartaIcon(R.drawable.ic_more_vert, contentDescription = stringResource(R.string.conv_menu_a11y), tint = c.ink, size = 22.dp)
                        }
                    }
                }

                if (sessions.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(bottom = VSpace.xxxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        VaartaIcon(R.drawable.ic_nav_chat, contentDescription = null, tint = c.faint, size = 40.dp)
                        Spacer(Modifier.height(VSpace.md))
                        Text(stringResource(R.string.conv_empty_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(VSpace.xs))
                        Text(
                            stringResource(R.string.conv_empty_body),
                            style = MaterialTheme.typography.bodyMedium, color = c.muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(VSpace.sm), modifier = Modifier.fillMaxWidth()) {
                        if (thisWeek.isNotEmpty()) {
                            item(key = "h_week") { Eyebrow(stringResource(R.string.conv_section_week)) }
                            items(thisWeek, key = { it.id }) { s -> SwipeableRow(s, onOpen = { onOpen(s) }, onDelete = { requestDelete(s.id) }) }
                        }
                        if (earlier.isNotEmpty()) {
                            item(key = "h_earlier") { Eyebrow(stringResource(R.string.conv_section_earlier)) }
                            items(earlier, key = { it.id }) { s -> SwipeableRow(s, onOpen = { onOpen(s) }, onDelete = { requestDelete(s.id) }) }
                        }
                        item(key = "foot") {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = VSpace.lg),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VaartaIcon(R.drawable.ic_lock, contentDescription = null, tint = c.faint, size = 14.dp)
                                Spacer(Modifier.width(VSpace.xs))
                                Text(stringResource(R.string.conv_encrypted), style = MaterialTheme.typography.bodySmall, color = c.faint)
                            }
                            Spacer(Modifier.height(72.dp)) // clear the FAB
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onNewChat,
                containerColor = c.indigo,
                contentColor = Color.White,
                icon = { VaartaIcon(R.drawable.ic_plus, contentDescription = null, tint = Color.White, size = 20.dp) },
                text = { Text(stringResource(R.string.conv_new_chat), style = MaterialTheme.typography.titleMedium, color = Color.White) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(VSpace.xl),
            )

            SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl),
                verticalArrangement = Arrangement.spacedBy(VSpace.md),
            ) {
                Text(stringResource(R.string.conv_menu_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(stringResource(R.string.conv_autodelete), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                    val options = listOf(0 to stringResource(R.string.conv_keep), 7 to stringResource(R.string.conv_7d), 30 to stringResource(R.string.conv_30d))
                    for ((days, label) in options) {
                        FilterChip(selected = retentionDays == days, onClick = { historyVm.setRetentionDays(days) }, label = { Text(label) })
                    }
                }
                if (sessions.isNotEmpty()) {
                    Spacer(Modifier.height(VSpace.xs))
                    VaartaSecondaryButton(
                        text = stringResource(R.string.conv_delete_all),
                        onClick = { historyVm.deleteAll(); showMenu = false },
                        leadingIcon = R.drawable.ic_close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** A conversation row wrapped in swipe-to-delete; swiping either way triggers [onDelete]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(session: CallSessionEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = VaartaTheme.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.scamTint).padding(horizontal = VSpace.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                VaartaIcon(R.drawable.ic_close, contentDescription = null, tint = c.scam, size = 22.dp)
            }
        },
    ) {
        HistoryRow(session = session, onOpen = onOpen)
    }
}

/** One conversation row: source circle + single-line title + (verdict pill + relative time) + chevron. */
@Composable
private fun HistoryRow(session: CallSessionEntity, onOpen: () -> Unit) {
    val c = VaartaTheme.colors
    val level = levelFromName(session.finalLevel)
    val (glyph, chipTint) = when (session.source) {
        SessionSource.CHAT -> R.drawable.ic_nav_chat to c.indigo
        SessionSource.RECORDING -> R.drawable.ic_headphones to c.muted
        else -> R.drawable.ic_phone to c.verify
    }
    val chipBg = when (session.source) {
        SessionSource.CHAT -> c.indigoTint
        SessionSource.RECORDING -> c.track
        else -> c.verifyTint
    }
    val title = session.title
        ?: session.scamType
        ?: when (session.source) {
            SessionSource.CHAT -> "Chat"
            SessionSource.RECORDING -> "Recording"
            else -> "Live call"
        }
    val scored = session.source != SessionSource.CHAT && session.finalScore > 0
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) androidx.compose.foundation.BorderStroke(1.dp, c.line) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(VSpace.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
            Surface(color = chipBg, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(glyph, contentDescription = null, tint = chipTint, size = 20.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                    if (scored) {
                        Surface(color = levelColor(level), shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                        Text(levelText(level), style = MaterialTheme.typography.labelMedium, color = levelColor(level), maxLines = 1)
                        Text("·", style = MaterialTheme.typography.labelMedium, color = c.faint)
                    }
                    Text(
                        relativeTimeLabel(session.startedAtMs, System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1,
                    )
                }
            }
            VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 20.dp)
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
internal fun AnalyzeScreen(
    analyzerVm: AudioAnalyzerViewModel,
    historyVm: HistoryViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val state by analyzerVm.state.collectAsState()
    val context = LocalContext.current
    // The screen owns its own picker, so every entry point (Home card, Live button, reopening after
    // an analysis) can start a new analysis right here — no dead-end Idle state.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzerVm.analyze(uri)
    }

    VaartaSubScreen(title = "Analyze a recording", onBack = onBack) {
        when (val s = state) {
            AudioAnalyzerViewModel.UiState.Idle -> {
                Text(
                    "Pick a recorded call — VAARTA will transcribe it, check it against known scam " +
                        "patterns, and give you a verdict.",
                    style = MaterialTheme.typography.bodyMedium, color = VaartaTheme.colors.muted,
                )
                VaartaSecondaryButton(
                    text = "Pick a recording",
                    onClick = { picker.launch("audio/*") },
                    leadingIcon = R.drawable.ic_headphones,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AudioAnalyzerViewModel.UiState.Running -> {
                Spacer(Modifier.height(40.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VSpace.md)) {
                    CircularProgressIndicator(color = VaartaTheme.colors.indigo)
                    Text("Transcribing and checking the recording…", style = MaterialTheme.typography.titleMedium, color = VaartaTheme.colors.ink)
                    Text("This can take up to a minute for a longer clip.", style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
                }
            }

            is AudioAnalyzerViewModel.UiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.scamTint)) {
                    Text(s.message, modifier = Modifier.padding(VSpace.lg), style = MaterialTheme.typography.bodyMedium, color = VaartaTheme.colors.scam)
                }
                VaartaSecondaryButton(text = "Back", onClick = onBack, leadingIcon = R.drawable.ic_arrow_left, modifier = Modifier.fillMaxWidth())
            }

            is AudioAnalyzerViewModel.UiState.Done -> {
                val r = s.result
                // Auto-save the analyzed recording to Conversations (v2 — keyed on the result so it
                // saves once per analysis; the user picked the clip, which is the consent).
                LaunchedEffect(r) {
                    historyVm.save(SessionSource.RECORDING, r.score, r.level.name, r.scamType, r.chat) {
                        Toast.makeText(context, "Saved to your conversations", Toast.LENGTH_SHORT).show()
                    }
                }
                RiskHero(
                    level = r.level,
                    score = r.score,
                    reassure = r.reassure,
                    aiRaised = r.aiRaised,
                    detectedStages = r.detectedStages,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(
                    "Analyzed from a recording. The risk score is computed on-device from the transcript; " +
                        "the AI transcribed and helped classify it.",
                    style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted,
                )
                if (r.chat.isNotEmpty()) ChatThread(r.chat, onOpenUrl)

                if (r.level.ordinal >= RiskLevel.HIGH_RISK.ordinal && !r.reassure) {
                    VaartaButton(
                        text = "Share this warning",
                        onClick = { onShare("VAARTA: I analyzed a call recording and it looks like a scam (${levelText(r.level)}).") },
                        leadingIcon = R.drawable.ic_bell,
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm), modifier = Modifier.fillMaxWidth()) {
                    VaartaIcon(R.drawable.ic_check, contentDescription = null, tint = VaartaTheme.colors.muted, size = 16.dp)
                    Text("Saved to your conversations", style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

