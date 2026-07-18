package ai.vaarta.ui.components

import ai.vaarta.R
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The four emergency steps (redesign spec §6.2/§6.5) — defined once so Home's panic sheet and
 * Help's "happening now" link render identical copy. Never inline these strings elsewhere.
 */
@Composable
fun RightNowSteps(modifier: Modifier = Modifier) {
    val steps = listOf(
        stringResource(R.string.panic_step_1),
        stringResource(R.string.panic_step_2),
        stringResource(R.string.panic_step_3),
        stringResource(R.string.panic_step_4),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VSpace.md)) {
        steps.forEachIndexed { i, text -> RightNowStep(i + 1, text) }
    }
}

@Composable
private fun RightNowStep(number: Int, text: String) {
    val c = VaartaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(VSpace.md)) {
        Surface(color = c.scamTint, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.titleMedium, color = c.scam)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
    }
}

/**
 * The panic sheet body (redesign spec §6.2): [RightNowSteps] → **Call 1930 now** (the only button)
 * → one quiet "Get live help from VAARTA →" link row. Home's red banner and Help's emergency card
 * both open this same composable so the moment-of-panic guidance never drifts between screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicSheet(
    onDismissRequest: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartLive: () -> Unit,
) {
    val c = VaartaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = VSpace.xxl).padding(bottom = VSpace.xxxl),
            verticalArrangement = Arrangement.spacedBy(VSpace.md),
        ) {
            Text(stringResource(R.string.panic_heading), style = MaterialTheme.typography.headlineSmall, color = c.scam)
            RightNowSteps()
            Spacer(Modifier.height(VSpace.xs))
            VaartaButton(
                text = stringResource(R.string.panic_call_1930),
                onClick = { onOpenUrl("tel:1930") },
                leadingIcon = R.drawable.ic_phone,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextLinkRow(
                text = stringResource(R.string.panic_get_live_help),
                onClick = { onDismissRequest(); onStartLive() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
