package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.complaint.ChecklistItem
import ai.vaarta.complaint.ComplaintFlowState
import ai.vaarta.complaint.ComplaintFlowViewModel
import ai.vaarta.complaint.ComplaintStep
import ai.vaarta.complaint.IdentityDetails
import ai.vaarta.complaint.LossInput
import ai.vaarta.core.complaint.SlotSource
import ai.vaarta.core.reasoning.ComplaintDestination
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.vaartaPressable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Task 7: the complaint co-pilot's three-step flow host. Task 8 fills in REVIEW; FILE (Task 9)
 * still renders an empty stub for now so this compiles and is driveable end to end.
 */
@Composable
fun ComplaintFlowScreen(vm: ComplaintFlowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    VaartaSubScreen(title = "Report a scam", onBack = onBack, modifier = modifier) {
        when (state.step) {
            ComplaintStep.PREPARE -> PrepareStep(state, onSelect = vm::selectDestination, onContinue = vm::toReview)
            ComplaintStep.REVIEW -> ReviewStep(
                state = state,
                onSaveIdentity = vm::saveIdentity,
                onSetLoss = vm::setLoss,
                onContinue = vm::toFile,
            )
            ComplaintStep.FILE -> Column {}   // Task 9 fills this in
        }
    }
}

@Composable
private fun PrepareStep(
    state: ComplaintFlowState,
    onSelect: (ComplaintDestination) -> Unit,
    onContinue: () -> Unit,
) {
    val c = VaartaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(VSpace.md)) {
        Text(
            "Where to report",
            style = MaterialTheme.typography.titleLarge,
            color = c.ink,
        )
        Text(
            "Based on what happened, here's where this should go.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
        Spacer(Modifier.height(VSpace.xs))
        for (dest in state.destinations) {
            DestinationCard(dest = dest, selected = dest == state.selected, onClick = { onSelect(dest) })
        }
        Spacer(Modifier.height(VSpace.md))
        VaartaButton(
            text = "Continue →",
            onClick = onContinue,
            enabled = state.selected != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DestinationCard(dest: ComplaintDestination, selected: Boolean, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    val why = if (dest.phone != null) "Call ${dest.phone} now if money moved" else "Report this online"
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) c.indigoTint else c.panel),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) c.indigo else c.line),
        modifier = Modifier.fillMaxWidth().vaartaPressable(onClick),
    ) {
        Column(Modifier.padding(VSpace.lg)) {
            Text(dest.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(VSpace.xs))
            Text(why, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/**
 * Task 8: review + edit the assembled [ai.vaarta.complaint.ComplaintPacket] before filing.
 *
 * Local-edit approach for "Your complaint": [editedFields] starts empty and is keyed by
 * [ai.vaarta.complaint.FilledField.key]. Each field renders `editedFields[key] ?: field.value`, so it
 * shows the packet's live assembled value until the user types into it — after which their edit wins
 * even as the packet reassembles underneath (e.g. after identity or loss is saved elsewhere on this
 * same screen, which changes the very same `identity.*`/`loss.*` slots the playbook lists as fields).
 * Nothing downstream reads this map yet; it's a hoisted `Map<String, String>` so Task 9's File step
 * can take it as a parameter when it needs the edited text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewStep(
    state: ComplaintFlowState,
    onSaveIdentity: (IdentityDetails) -> Unit,
    onSetLoss: (LossInput) -> Unit,
    onContinue: () -> Unit,
) {
    val c = VaartaTheme.colors
    val packet = state.packet
    val editedFields = remember { mutableStateMapOf<String, String>() }
    var showIdentitySheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(VSpace.lg)) {
        Text("Review your complaint", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text(
            "Everything below goes into the ${packet?.destinationName ?: "complaint"} form. Check it, then continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )

        state.freshnessNote?.let { note ->
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cautionTint),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(VSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
                ) {
                    VaartaIcon(R.drawable.ic_alert_triangle, contentDescription = null, tint = c.caution, size = 18.dp)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = c.ink)
                }
            }
        }

        ReviewSection(title = "Your complaint") {
            val fields = packet?.fields.orEmpty()
            fields.forEachIndexed { i, field ->
                if (i > 0) Spacer(Modifier.height(VSpace.md))
                val value = editedFields[field.key] ?: field.value
                OutlinedTextField(
                    value = value,
                    onValueChange = { editedFields[field.key] = it },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (field.source != SlotSource.USER) {
                    Text("auto-filled — verify", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                if (field.minChars != null && value.length < field.minChars) {
                    Text(
                        "Needs at least ${field.minChars} characters (has ${value.length})",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.scam,
                    )
                }
            }
        }

        ReviewSection(title = "Your details") {
            val identity = state.identity
            if (identity == null) {
                Text(
                    "Saved once on this device, reused for every complaint you file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
                Spacer(Modifier.height(VSpace.sm))
                VaartaSecondaryButton(
                    text = "Add your details once",
                    onClick = { showIdentitySheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(identity.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(identity.mobile, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Spacer(Modifier.height(VSpace.xs))
                TextLinkRow(text = "Edit", onClick = { showIdentitySheet = true })
            }
        }

        if (state.selected?.requiresMoneyLost == true) {
            ReviewSection(title = "Money lost?") {
                MoneyLostFields(loss = state.loss, onSetLoss = onSetLoss)
            }
        }

        ReviewSection(title = "Documents") {
            val checklist = packet?.checklist.orEmpty()
            checklist.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(VSpace.md))
                DocumentRow(item)
            }
        }

        Spacer(Modifier.height(VSpace.xs))
        VaartaButton(text = "Continue to file →", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(VSpace.md))
    }

    if (showIdentitySheet) {
        IdentitySheet(
            initial = state.identity,
            onDismiss = { showIdentitySheet = false },
            onSave = { details ->
                onSaveIdentity(details)
                showIdentitySheet = false
            },
        )
    }
}

/** One bordered content card matching [DestinationCard]'s visual language — title + body. */
@Composable
private fun ReviewSection(title: String, content: @Composable () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(VSpace.lg)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(VSpace.md))
            content()
        }
    }
}

/**
 * Amount / transaction-ID / transaction-date, pushed to the ViewModel on every keystroke (spec:
 * "your call, simplest correct approach"). Seeded once from [loss] with no re-key on recomposition —
 * these three slots are user-authored only (nothing else in the flow writes them), so there is no
 * autofill to reconcile with, and re-seeding on every [loss] change would fight the user's typing
 * whenever a keystroke produces a value that doesn't round-trip cleanly (e.g. a non-numeric amount).
 */
@Composable
private fun MoneyLostFields(loss: LossInput?, onSetLoss: (LossInput) -> Unit) {
    var amount by remember { mutableStateOf(loss?.amountInr?.toString() ?: "") }
    var txnId by remember { mutableStateOf(loss?.txnId ?: "") }
    var txnDate by remember { mutableStateOf(loss?.txnDate ?: "") }

    fun push() = onSetLoss(LossInput(amount.toLongOrNull(), txnId.ifBlank { null }, txnDate.ifBlank { null }))

    OutlinedTextField(
        value = amount,
        onValueChange = { amount = it; push() },
        label = { Text("Amount lost (INR)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(VSpace.sm))
    OutlinedTextField(
        value = txnId,
        onValueChange = { txnId = it; push() },
        label = { Text("Transaction ID / UTR") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(VSpace.sm))
    OutlinedTextField(
        value = txnDate,
        onValueChange = { txnDate = it; push() },
        label = { Text("Transaction date") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A document checklist row: filled circle + check for what VAARTA already has, outline + alert for what the user must attach. */
@Composable
private fun DocumentRow(item: ChecklistItem) {
    val c = VaartaTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
        val icon = if (item.providedByVaarta) R.drawable.ic_check else R.drawable.ic_alert_triangle
        val tint = if (item.providedByVaarta) c.safe else c.caution
        val badgeBg = if (item.providedByVaarta) c.safeTint else c.cautionTint
        Surface(shape = CircleShape, color = badgeBg, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                VaartaIcon(icon, contentDescription = null, tint = tint, size = 16.dp)
            }
        }
        Column {
            Text(
                if (item.providedByVaarta) "${item.label} — provided by VAARTA" else "${item.label} — you attach",
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
            )
            Text(item.note, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/** Name/address/mobile/email/ID-type — one sheet reused for both first-time add and later edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentitySheet(
    initial: IdentityDetails?,
    onDismiss: () -> Unit,
    onSave: (IdentityDetails) -> Unit,
) {
    val c = VaartaTheme.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var mobile by remember { mutableStateOf(initial?.mobile ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var idType by remember { mutableStateOf(initial?.idType ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl),
            verticalArrangement = Arrangement.spacedBy(VSpace.md),
        ) {
            Text("Your details", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(
                "Saved once on this device, reused for every complaint.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = idType,
                onValueChange = { idType = it },
                label = { Text("ID type (Aadhaar / PAN / Voter / DL / Passport)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(VSpace.sm))
            VaartaButton(
                text = "Save",
                onClick = { onSave(IdentityDetails(name, address, mobile, email, idType)) },
                enabled = name.isNotBlank() && mobile.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
