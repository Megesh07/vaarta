package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.SessionViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.guardian.Guardian
import ai.vaarta.guardian.GuardianPickerContract
import ai.vaarta.guardian.GuardianStore
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.share.BilingualShare
import ai.vaarta.ui.components.ConfirmDialog
import ai.vaarta.ui.components.LinkRow
import ai.vaarta.ui.components.PanicSheet
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Plain, calm steps for someone who has already been defrauded (spec §4.3). Ordered by urgency —
 * stop the bleeding, then report fast (1930's money-freeze window), then bank + evidence. Procedural
 * safety guidance only; deliberately no financial advice.
 */
private val SCAMMED_STEP_IDS = listOf(
    R.string.help_scammed_step_1,
    R.string.help_scammed_step_2,
    R.string.help_scammed_step_3,
    R.string.help_scammed_step_4,
    R.string.help_scammed_step_5,
    R.string.help_scammed_step_6,
    R.string.help_scammed_step_7,
)

/**
 * The social-good pillar (spec §4.3): how and where to get help and report a scam, always reachable.
 * The complaint draft (reused from the deterministic engine) lives here now, off the live screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    vm: SessionViewModel,
    onShare: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val scroll = rememberScrollState()
    val complaint by vm.session.complaint.collectAsState()
    val complaintDraft by vm.session.complaintDraft.collectAsState()
    var showPanic by remember { mutableStateOf(false) }
    var showAllSteps by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showClearVoice by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(AppLanguage.current()) }

    // Guardian contact picker (Task 5, spec §7; Task 9 hardening fix) — a one-time, changeable
    // choice so "Warn your family" can skip the share chooser next time. GuardianStore wraps the
    // encrypted SQLCipher database (core:data), so every read/write is a suspend call; the picker
    // itself needs no READ_CONTACTS — the system grants a temporary read on the picked Phone-data
    // URI (GuardianPickerContract).
    val context = LocalContext.current
    val guardianStore = remember { GuardianStore.create(context) }
    val scope = rememberCoroutineScope()
    var guardian by remember { mutableStateOf<Guardian?>(null) }
    LaunchedEffect(Unit) { guardian = guardianStore.get() }
    val guardianPicker = rememberLauncherForActivityResult(GuardianPickerContract()) { picked ->
        if (picked != null) {
            scope.launch {
                guardianStore.set(picked.name, picked.number)
                guardian = picked
            }
        }
    }
    fun pickGuardian() {
        guardianPicker.launch(Unit)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = VSpace.xl),
            verticalArrangement = Arrangement.spacedBy(VSpace.lg),
        ) {
            Spacer(Modifier.height(VSpace.sm))
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)

            // Emergency (redesign spec §6.5) — compact red-tinted card. The 4-step guidance itself
            // lives once in the shared panic sheet (spec §6.2), opened here so copy never drifts.
            Card(
                colors = CardDefaults.cardColors(containerColor = c.scamTint),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(VSpace.lg)) {
                    Text(stringResource(R.string.help_emergency_title), style = MaterialTheme.typography.titleLarge, color = c.scam)
                    Spacer(Modifier.height(VSpace.md))
                    VaartaButton(
                        text = stringResource(R.string.help_call_1930),
                        onClick = { onOpenUrl("tel:1930") },
                        leadingIcon = R.drawable.ic_phone,
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextLinkRow(
                        text = stringResource(R.string.help_emergency_open_steps),
                        onClick = { showPanic = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HelpSection(title = stringResource(R.string.help_lost_money_title)) {
                Text(
                    stringResource(R.string.help_lost_money_intro),
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.md))
                val visibleStepIds = if (showAllSteps) SCAMMED_STEP_IDS else SCAMMED_STEP_IDS.take(3)
                visibleStepIds.forEachIndexed { i, stepId ->
                    if (i > 0) Spacer(Modifier.height(VSpace.md))
                    StepRow(number = i + 1, text = stringResource(stepId))
                }
                Spacer(Modifier.height(VSpace.md))
                TextLinkRow(
                    text = stringResource(
                        if (showAllSteps) R.string.help_show_fewer_steps else R.string.help_show_all_steps,
                        SCAMMED_STEP_IDS.size,
                    ),
                    onClick = { showAllSteps = !showAllSteps },
                )
                Spacer(Modifier.height(VSpace.xs))
                Text(
                    stringResource(R.string.help_lost_money_footer),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }

            HelpSection(title = stringResource(R.string.help_report_title)) {
                LinkRow(
                    icon = R.drawable.ic_globe,
                    title = stringResource(R.string.help_report_cybercrime),
                    onClick = { onOpenUrl("https://cybercrime.gov.in") },
                )
                LinkRow(
                    icon = R.drawable.ic_globe,
                    title = stringResource(R.string.help_report_chakshu),
                    onClick = { onOpenUrl("https://sancharsaathi.gov.in") },
                )
                Spacer(Modifier.height(VSpace.xs))
                Text(
                    stringResource(R.string.help_report_caption),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }

            HelpSection(title = stringResource(R.string.help_tools_title)) {
                LinkRow(
                    icon = R.drawable.ic_file_text,
                    title = stringResource(R.string.help_tools_complaint),
                    subtitle = stringResource(R.string.help_tools_complaint_sub),
                    onClick = { vm.session.generateComplaint() },
                )
                complaint?.let { text ->
                    Spacer(Modifier.height(VSpace.sm))
                    Card(colors = CardDefaults.cardColors(containerColor = c.panel)) {
                        Column(Modifier.padding(VSpace.md)) {
                            // Edge case 1 (spec §3B.3): the complaint filing itself stays English —
                            // cybercrime.gov.in is English-first — but the reason why is localized.
                            if (currentLanguage != AppLanguage.ENGLISH) {
                                Text(
                                    stringResource(R.string.help_complaint_english_note),
                                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                                )
                                Spacer(Modifier.height(VSpace.sm))
                            }
                            Text(text, style = MaterialTheme.typography.bodySmall, color = c.ink)
                            Spacer(Modifier.height(VSpace.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                                VaartaButton(text = stringResource(R.string.help_share_as_text), onClick = { onShare(text) })
                                complaintDraft?.let { draft ->
                                    VaartaSecondaryButton(text = stringResource(R.string.help_export_pdf), onClick = { onExportPdf(draft) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(VSpace.sm))
                }
                val warnFamilyMessage = stringResource(R.string.help_warn_family_message)
                LinkRow(
                    icon = R.drawable.ic_bell,
                    title = stringResource(R.string.help_tools_warn_family),
                    subtitle = stringResource(R.string.help_tools_warn_family_sub),
                    onClick = { onShare(BilingualShare.compose(warnFamilyMessage, currentLanguage)) },
                )
                Spacer(Modifier.height(VSpace.xs))
                LinkRow(
                    icon = R.drawable.ic_phone,
                    title = stringResource(R.string.guardian_row_title),
                    subtitle = guardian?.name ?: stringResource(R.string.guardian_not_set),
                    onClick = { pickGuardian() },
                )
                if (guardian != null) {
                    TextLinkRow(
                        text = stringResource(R.string.guardian_clear),
                        onClick = {
                            scope.launch {
                                guardianStore.clear()
                                guardian = null
                            }
                        },
                    )
                }
            }
            HelpSection(title = "") {
                HelpLanguageRow(current = currentLanguage, onClick = { showLanguagePicker = true })
            }

            // Voice-attribution privacy control (Part D). Same destructive-action idiom as the
            // Conversations screen's "Delete all" row (MainActivity.kt HistoryScreen kebab sheet):
            // now gated behind a confirmation dialog.
            HelpSection(title = "") {
                Text(
                    stringResource(R.string.settings_clear_voice_data_desc),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.sm))
                VaartaSecondaryButton(
                    text = stringResource(R.string.settings_clear_voice_data),
                    onClick = { showClearVoice = true },
                    leadingIcon = R.drawable.ic_close,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(VSpace.xxl))
        }
    }

    if (showPanic) {
        PanicSheet(
            onDismissRequest = { showPanic = false },
            onOpenUrl = onOpenUrl,
            onStartLive = onStartLive,
        )
    }

    if (showLanguagePicker) {
        ModalBottomSheet(
            onDismissRequest = { showLanguagePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl)) {
                Text(stringResource(R.string.language_picker_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(VSpace.md))
                LanguageOptionsList(onSelect = { language ->
                    currentLanguage = language
                    showLanguagePicker = false
                    AppLanguage.apply(language) // AppCompatActivity recreates itself — no manual recreate()
                })
            }
        }
    }

    ConfirmDialog(
        visible = showClearVoice,
        title = stringResource(R.string.confirm_clear_voice_title),
        body = stringResource(R.string.confirm_clear_voice_body),
        confirmLabel = stringResource(R.string.settings_clear_voice_data),
        onConfirm = { vm.session.clearVoiceData() },
        onDismiss = { showClearVoice = false },
    )
}

/** A single numbered step: a calm circular badge + the instruction, aligned as a row. */
@Composable
private fun StepRow(number: Int, text: String) {
    val c = VaartaTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.padding(top = VSpace.xs))
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) BorderStroke(1.dp, c.line) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(VSpace.lg)) {
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(VSpace.sm))
            }
            content()
        }
    }
}
