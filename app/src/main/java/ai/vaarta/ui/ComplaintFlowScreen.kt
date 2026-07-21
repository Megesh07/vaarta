package ai.vaarta.ui

import ai.vaarta.complaint.ComplaintFlowState
import ai.vaarta.complaint.ComplaintFlowViewModel
import ai.vaarta.complaint.ComplaintStep
import ai.vaarta.core.reasoning.ComplaintDestination
import ai.vaarta.ui.components.VaartaButton
import ai.vaarta.ui.components.VaartaSubScreen
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.vaartaPressable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Task 7: the complaint co-pilot's three-step flow host. Only PREPARE is built here — REVIEW
 * (Task 8) and FILE (Task 9) render empty stubs for now so this compiles and is driveable end to end.
 */
@Composable
fun ComplaintFlowScreen(vm: ComplaintFlowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    VaartaSubScreen(title = "Report a scam", onBack = onBack, modifier = modifier) {
        when (state.step) {
            ComplaintStep.PREPARE -> PrepareStep(state, onSelect = vm::selectDestination, onContinue = vm::toReview)
            ComplaintStep.REVIEW -> Column {} // Task 8 fills this in
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
