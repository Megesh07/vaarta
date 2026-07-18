package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.SessionViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.ui.components.PanicSheet
import ai.vaarta.ui.components.TextLinkRow
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSecondaryButton
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val WARN_FAMILY_MESSAGE =
    "VAARTA: please be careful — scammers posing as police/CBI/courier are calling people, " +
        "threatening arrest, and demanding money or OTPs. No real officer arrests anyone over a " +
        "phone or video call. Never pay or share an OTP. If pressured, hang up and call 1930."

/**
 * Plain, calm steps for someone who has already been defrauded (spec §4.3). Ordered by urgency —
 * stop the bleeding, then report fast (1930's money-freeze window), then bank + evidence. Procedural
 * safety guidance only; deliberately no financial advice.
 */
private val SCAMMED_STEPS = listOf(
    "Stop now — don't send any more money. Scammers often demand \"one last payment\" to reverse " +
        "it. That is part of the scam.",
    "Call 1930 right away and report it. The sooner you call, the better the chance of stopping " +
        "the money.",
    "Tell your bank immediately. Ask them to freeze the transaction and block any further debits.",
    "File a complaint on cybercrime.gov.in with the numbers, transaction IDs, and any screenshots.",
    "If you shared an OTP, PIN, or password, change it and turn on any extra security your bank offers.",
    "Keep everything — call logs, messages, and payment receipts — as evidence.",
    "Tell your family so they can help and stay alert too.",
)

/**
 * The social-good pillar (spec §4.3): how and where to get help and report a scam, always reachable.
 * The complaint draft (reused from the deterministic engine) lives here now, off the live screen.
 */
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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = VSpace.xl),
            verticalArrangement = Arrangement.spacedBy(VSpace.lg),
        ) {
            Spacer(Modifier.height(VSpace.sm))
            Text("Get help & report", style = MaterialTheme.typography.headlineMedium, color = c.ink)

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
                        text = "Call 1930",
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

            HelpSection(title = "If you've already lost money") {
                Text(
                    "Stay calm — acting fast can still get your money back. Do these in order:",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.md))
                SCAMMED_STEPS.forEachIndexed { i, step ->
                    if (i > 0) Spacer(Modifier.height(VSpace.md))
                    StepRow(number = i + 1, text = step)
                }
                Spacer(Modifier.height(VSpace.md))
                Text(
                    "Reporting within the first few hours gives the best chance of freezing the " +
                        "money before it moves.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }

            HelpSection(title = "Report online") {
                Text(
                    "File a complaint on the National Cyber Crime Reporting Portal.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.md))
                VaartaSecondaryButton(
                    text = "Open cybercrime.gov.in",
                    onClick = { onOpenUrl("https://cybercrime.gov.in") },
                    leadingIcon = R.drawable.ic_globe,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HelpSection(title = "Prepare a complaint") {
                Text(
                    "Turn what VAARTA detected into a ready-to-file complaint draft.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.md))
                VaartaSecondaryButton(
                    text = "Generate complaint draft",
                    onClick = { vm.session.generateComplaint() },
                    leadingIcon = R.drawable.ic_file_text,
                    modifier = Modifier.fillMaxWidth(),
                )
                complaint?.let { text ->
                    Spacer(Modifier.height(VSpace.md))
                    Card(colors = CardDefaults.cardColors(containerColor = c.panel)) {
                        Column(Modifier.padding(VSpace.md)) {
                            Text(text, style = MaterialTheme.typography.bodySmall, color = c.ink)
                            Spacer(Modifier.height(VSpace.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(VSpace.sm)) {
                                VaartaButton(text = "Share as text", onClick = { onShare(text) })
                                complaintDraft?.let { draft ->
                                    VaartaSecondaryButton(text = "Export PDF", onClick = { onExportPdf(draft) })
                                }
                            }
                        }
                    }
                }
            }

            HelpSection(title = "Warn your family") {
                Text(
                    "Send a short warning to the people most at risk — one message can stop a scam.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(VSpace.md))
                VaartaSecondaryButton(
                    text = "Share a warning",
                    onClick = { onShare(WARN_FAMILY_MESSAGE) },
                    leadingIcon = R.drawable.ic_bell,
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
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(VSpace.sm))
            content()
        }
    }
}
