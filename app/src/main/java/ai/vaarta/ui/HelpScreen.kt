package ai.vaarta.ui

import ai.vaarta.SessionViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val WARN_FAMILY_MESSAGE =
    "VAARTA: please be careful — scammers posing as police/CBI/courier are calling people, " +
        "threatening arrest, and demanding money or OTPs. No real officer arrests anyone over a " +
        "phone or video call. Never pay or share an OTP. If pressured, hang up and call 1930."

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
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val scroll = rememberScrollState()
    val complaint by vm.session.complaint.collectAsState()
    val complaintDraft by vm.session.complaintDraft.collectAsState()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Get help & report", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.ink)

            HelpSection(title = "If this is happening now") {
                Text(
                    "Call the government cyber-crime helpline. It's free and open 24×7.",
                    fontSize = 14.sp, color = c.muted,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenUrl("tel:1930") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.scam),
                ) { Text("📞  Call 1930", fontSize = 16.sp) }
            }

            HelpSection(title = "Report online") {
                Text(
                    "File a complaint on the National Cyber Crime Reporting Portal.",
                    fontSize = 14.sp, color = c.muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onOpenUrl("https://cybercrime.gov.in") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🌐  Open cybercrime.gov.in") }
            }

            HelpSection(title = "Prepare a complaint") {
                Text(
                    "Turn what VAARTA detected into a ready-to-file complaint draft.",
                    fontSize = 14.sp, color = c.muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { vm.session.generateComplaint() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📝  Generate complaint draft") }
                complaint?.let { text ->
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = c.panel)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text, fontSize = 12.sp, color = c.ink)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onShare(text) }) { Text("Share as text") }
                                complaintDraft?.let { draft ->
                                    OutlinedButton(onClick = { onExportPdf(draft) }) { Text("Export PDF") }
                                }
                            }
                        }
                    }
                }
            }

            HelpSection(title = "Warn your family") {
                Text(
                    "Send a short warning to the people most at risk — one message can stop a scam.",
                    fontSize = 14.sp, color = c.muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onShare(WARN_FAMILY_MESSAGE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🔔  Share a warning") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    val c = VaartaTheme.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
