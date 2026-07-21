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
import ai.vaarta.guardian.GuardianStore
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.panic.PanicViewModel
import ai.vaarta.share.BilingualShare
import ai.vaarta.ui.FirstRunLanguagePicker
import ai.vaarta.ui.RiskHero
import ai.vaarta.ui.rememberIsOnline
import ai.vaarta.ui.VaartaIcon
import ai.vaarta.ui.VaartaNav
import ai.vaarta.ui.components.ConfirmDialog
import ai.vaarta.ui.components.Eyebrow
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaBackBar
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.vaartaPressable
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Extends AppCompatActivity (not the plain ComponentActivity) specifically so per-app language
 * (redesign spec §3B.1) actually works: AppCompatDelegate's locale-resource wrapping is applied via
 * AppCompatActivity#attachBaseContext — without it, setApplicationLocales() + recreate() silently
 * relaunches the activity with the OLD locale's resources on API < 33 (caught live on the emulator).
 */
class MainActivity : AppCompatActivity() {
    private val vm: SessionViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()
    private val conversationVm: ConversationViewModel by viewModels()
    private val awarenessVm: AwarenessViewModel by viewModels()
    private val panicVm: PanicViewModel by viewModels()

    // Restore signal (redesign spec §B3): bumped every time an Intent asks to jump to the live page
    // (bubble tap or notification tap, both carry [OverlayService.EXTRA_OPEN_LIVE]) — a counter, not a
    // boolean, so VaartaNav's LaunchedEffect fires on every restore, not just the first.
    private val openLiveRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // system-bar icons follow light/dark correctly (spec §8.1)
        handleOpenLiveIntent(intent)
        setContent {
            VaartaTheme {
                // One-time, not-skippable language choice (redesign spec §3B.1) — gates the rest of
                // the app on first launch only; permanently reachable afterward from Help.
                var languageChosen by remember { mutableStateOf(AppLanguage.hasBeenChosen()) }
                if (!languageChosen) {
                    FirstRunLanguagePicker(onChosen = { languageChosen = true })
                } else {
                    VaartaNav(vm, historyVm, conversationVm, awarenessVm, panicVm, openLiveRequests = openLiveRequests, onShare = ::warnFamily, onShareGeneric = ::shareText, onExportPdf = ::exportAndSharePdf, onOpenUrl = ::openUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenLiveIntent(intent)
    }

    /** Restore (redesign spec §B3): the bubble/notification's Intent carries [OverlayService
     *  .EXTRA_OPEN_LIVE] — bump the signal so VaartaNav jumps to the live page, and tell the service
     *  to hide the bubble (it keeps running as the mic host; only its window goes away). */
    private fun handleOpenLiveIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(OverlayService.EXTRA_OPEN_LIVE, false) == true) {
            openLiveRequests.value++
            OverlayService.hideBubble(this)
        }
    }

    /**
     * The single entry point behind every "Warn your family" action (Live's alert-family, the Article
     * and Help screen rows — all wired to `onShare`). Task 5, spec §7: if
     * a guardian contact has been chosen, skip the "who do I send this to" friction entirely and open
     * a direct SMS pre-filled with the message; otherwise this is exactly today's behavior, unchanged.
     * Task 9 hardening fix: guardian lookup now reads the encrypted SQLCipher-backed [GuardianStore]
     * (suspend), so this launches on [lifecycleScope] instead of reading synchronously.
     */
    private fun warnFamily(text: String) {
        lifecycleScope.launch {
            val guardian = GuardianStore.create(this@MainActivity).get()
            if (guardian != null) {
                val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${guardian.number}")).apply {
                    putExtra("sms_body", text)
                }
                val opened = runCatching { startActivity(sms) }.isSuccess
                if (opened) return@launch
                // No SMS app can handle smsto: (rare) — fall back to the chooser rather than silently
                // dropping the alert.
            }
            shareText(text)
        }
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.live_share_via)))
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
        startActivity(Intent.createChooser(send, getString(R.string.live_share_complaint_pdf)))
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
    val isOnline by rememberIsOnline()
    val aiSuggestion by vm.session.aiSuggestion.collectAsState()
    val aiLoading by vm.session.aiLoading.collectAsState()
    val liveStatus by vm.session.liveStatus.collectAsState()
    val showSpeakerNudge by vm.session.showSpeakerNudge.collectAsState()
    val chat by vm.session.chat.collectAsState()
    val scroll = rememberScrollState()
    // Only fires once per session (CopilotSession.nudgeShown), so a simple local dismiss is enough —
    // nothing needs to signal back into the session.
    var speakerNudgeDismissed by remember { mutableStateOf(false) }

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
                Toast.makeText(context, context.getString(R.string.live_saved), Toast.LENGTH_SHORT).show()
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
                    R.drawable.ic_arrow_left, contentDescription = stringResource(R.string.common_back), tint = VaartaTheme.colors.ink, size = 24.dp,
                    modifier = Modifier.vaartaPressable(onBack).padding(end = VSpace.md),
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

            if (showSpeakerNudge && !speakerNudgeDismissed) {
                SpeakerNudgeBanner(onDismiss = { speakerNudgeDismissed = true })
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
                            Text(stringResource(R.string.live_ai_thinking), style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
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
                    // Active: the ring + pulsing dot above already say "this is live". Minimize keeps
                    // the SAME shared session running behind the dialer (redesign spec §B2/§B3) —
                    // launchFloating() already handles the overlay/mic permission hops.
                    Text(
                        stringResource(R.string.live_active_caption),
                        style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted,
                    )
                    VaartaSecondaryButton(
                        text = stringResource(R.string.live_float),
                        onClick = { launchFloating() },
                        leadingIcon = R.drawable.ic_pip,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VaartaSecondaryButton(
                        text = stringResource(R.string.live_stop),
                        onClick = {
                            vm.session.stopLiveListening()
                            OverlayService.stop(context) // no-op if it was never running
                        },
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
                    // No opt-in toggle: live protection is always intelligent when online, and honestly
                    // reports "offline — on-device only" with no internet (auto-resumes on reconnect).
                    if (vm.session.aiConfigured) {
                        LiveIntelligenceStatus(isOnline = isOnline)
                    }
                }
            }

            if (displayedLevel.ordinal >= RiskLevel.HIGH_RISK.ordinal && !reassure) {
                val alertMessage = stringResource(R.string.live_alert_family_message, levelText(displayedLevel))
                VaartaButton(
                    text = stringResource(R.string.live_alert_family),
                    onClick = { onShare(BilingualShare.compose(alertMessage, AppLanguage.current())) },
                    leadingIcon = R.drawable.ic_bell,
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.live_no_agency_arrests),
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
        modifier = Modifier.fillMaxWidth().vaartaPressable(onCycle),
        colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.indigoTint),
    ) {
        Column(Modifier.padding(VSpace.md)) {
            Eyebrow(stringResource(R.string.live_ask_them))
            Spacer(Modifier.height(VSpace.xs))
            Text(text, style = MaterialTheme.typography.titleMedium, color = VaartaTheme.colors.ink)
            Spacer(Modifier.height(VSpace.xs))
            Text(stringResource(R.string.live_tap_another_question), style = MaterialTheme.typography.bodySmall, color = VaartaTheme.colors.muted)
        }
    }
}

/**
 * Honest live-intelligence status (live-session redesign 2026-07-21) — replaces the old opt-in
 * toggle. Live protection is always intelligent when online; with no internet it says so plainly and
 * keeps running the on-device engine, auto-resuming AI when the connection returns.
 */
@Composable
private fun LiveIntelligenceStatus(isOnline: Boolean) {
    val c = VaartaTheme.colors
    Card(colors = CardDefaults.cardColors(containerColor = if (isOnline) c.indigoTint else c.panel)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(VSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
        ) {
            VaartaIcon(
                if (isOnline) R.drawable.ic_sparkle else R.drawable.ic_nav_shield,
                contentDescription = null,
                tint = if (isOnline) c.indigoInk else c.muted,
                size = 18.dp,
            )
            Text(
                stringResource(if (isOnline) R.string.live_ai_online else R.string.live_ai_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isOnline) c.indigoInk else c.muted,
            )
        }
    }
}

/** Speaker-off nudge (redesign spec §6.4, Part D): the voiceprint match ratio suggests the mic is
 *  mostly hearing the user, not the caller — dismissible, same Card+icon-row idiom as [AiConsentRow]. */
@Composable
private fun SpeakerNudgeBanner(onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = VaartaTheme.colors.indigoTint)) {
        Row(modifier = Modifier.fillMaxWidth().padding(VSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                VaartaIcon(R.drawable.ic_mic, contentDescription = null, tint = VaartaTheme.colors.indigoInk, size = 18.dp)
                Text(
                    stringResource(R.string.live_active_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = VaartaTheme.colors.ink,
                )
            }
            VaartaIcon(
                R.drawable.ic_close, contentDescription = stringResource(R.string.chat_remove_a11y), tint = VaartaTheme.colors.indigoInk, size = 16.dp,
                modifier = Modifier.vaartaPressable(onDismiss),
            )
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
    var showDeleteAll by remember { mutableStateOf(false) }
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
                        modifier = Modifier.size(44.dp).vaartaPressable({ showMenu = true }),
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
                        onClick = { showDeleteAll = true },
                        leadingIcon = R.drawable.ic_close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    ConfirmDialog(
        visible = showDeleteAll,
        title = stringResource(R.string.confirm_delete_all_title),
        body = stringResource(R.string.confirm_delete_all_body),
        confirmLabel = stringResource(R.string.conv_delete_all),
        onConfirm = { historyVm.deleteAll(); showMenu = false },
        onDismiss = { showDeleteAll = false },
    )
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
            SessionSource.CHAT -> stringResource(R.string.conv_source_chat)
            SessionSource.RECORDING -> stringResource(R.string.conv_source_recording)
            else -> stringResource(R.string.conv_source_live)
        }
    val scored = session.source != SessionSource.CHAT && session.finalScore > 0
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) androidx.compose.foundation.BorderStroke(1.dp, c.line) else null,
        modifier = Modifier.fillMaxWidth().vaartaPressable(onOpen),
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VSpace.xs)) {
                    if (scored) {
                        Surface(color = levelColor(level), shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                        Text(
                            levelText(level), style = MaterialTheme.typography.labelMedium, color = levelColor(level),
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("·", style = MaterialTheme.typography.labelMedium, color = c.faint)
                    }
                    Text(
                        relativeTimeLabel(session.startedAtMs, System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelMedium, color = c.muted,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 20.dp)
        }
    }
}


