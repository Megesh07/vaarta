package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.SessionViewModel
import ai.vaarta.complaint.IdentityDetails
import ai.vaarta.complaint.IdentityStore
import ai.vaarta.guardian.Guardian
import ai.vaarta.guardian.GuardianPickerContract
import ai.vaarta.guardian.GuardianStore
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.ui.components.ConfirmDialog
import ai.vaarta.ui.components.LinkRow
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Task 10: the settings-y rows Help used to carry directly — app language, guardian contact
 * management (pick/clear), the complaint co-pilot's saved filing details, and privacy controls.
 * "Warn your family" and the guardian *read* used to size its subtitle stay in [HelpScreen]; only
 * the management UI (pick a new contact / remove it) lives here, exactly like the plan's
 * guardian-row split.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SessionViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = VaartaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentLanguage by remember { mutableStateOf(AppLanguage.current()) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    // Guardian contact (Task 5, spec §7; Task 9 hardening fix) — moved here from Help (Task 10).
    // The picker itself needs no READ_CONTACTS — the system grants a temporary read on the picked
    // Phone-data URI (GuardianPickerContract).
    val guardianStore = remember { GuardianStore.create(context) }
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

    // Your filing details (Task 10, new) — the complaint co-pilot's saved identity (Task 8),
    // reusing the same [IdentitySheet] Review shows rather than a second copy of the form.
    val identityStore = remember { IdentityStore.create(context) }
    var identity by remember { mutableStateOf<IdentityDetails?>(null) }
    LaunchedEffect(Unit) { identity = identityStore.get() }
    var showIdentitySheet by remember { mutableStateOf(false) }

    var showClearVoice by remember { mutableStateOf(false) }

    VaartaSubScreen(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        SettingsSection(title = "") {
            HelpLanguageRow(current = currentLanguage, onClick = { showLanguagePicker = true })
        }

        SettingsSection(title = stringResource(R.string.guardian_row_title)) {
            Text(
                stringResource(R.string.settings_guardian_desc),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            Spacer(Modifier.height(VSpace.sm))
            LinkRow(
                icon = R.drawable.ic_phone,
                title = guardian?.name ?: stringResource(R.string.guardian_not_set),
                onClick = { guardianPicker.launch(Unit) },
            )
            if (guardian != null) {
                TextLinkRow(
                    text = stringResource(R.string.guardian_clear),
                    onClick = { scope.launch { guardianStore.clear(); guardian = null } },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_your_details)) {
            Text(
                stringResource(R.string.settings_your_details_sub),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            Spacer(Modifier.height(VSpace.sm))
            val savedIdentity = identity
            if (savedIdentity == null) {
                VaartaSecondaryButton(
                    text = stringResource(R.string.settings_your_details_add),
                    onClick = { showIdentitySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(savedIdentity.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(savedIdentity.mobile, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Spacer(Modifier.height(VSpace.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(VSpace.lg)) {
                    TextLinkRow(text = stringResource(R.string.settings_your_details_edit), onClick = { showIdentitySheet = true })
                    TextLinkRow(
                        text = stringResource(R.string.settings_your_details_clear),
                        onClick = { scope.launch { identityStore.clear(); identity = null } },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_privacy_title)) {
            // Clear conversations lives on (Task 4's) Conversations kebab sheet, not here — its
            // delete-all/undo/retention state is local to HistoryScreen (MainActivity.kt) and
            // isn't easily pulled out without touching unrelated History code. Only clear-voice-data
            // (previously in Help) moved into this Privacy group.
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

    if (showIdentitySheet) {
        IdentitySheet(
            initial = identity,
            onDismiss = { showIdentitySheet = false },
            onSave = { details ->
                identity = details
                scope.launch { identityStore.set(details) }
                showIdentitySheet = false
            },
        )
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

/** One bordered/card content group, matching Help's own [HelpSection] visual language. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
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
