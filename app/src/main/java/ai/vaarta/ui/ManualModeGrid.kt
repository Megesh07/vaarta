package ai.vaarta.ui

import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Manual Mode as icon tiles (design system §2/§6) — replaces a wall of text chips with the same
 * glyph vocabulary [RiskHero] uses for live-detected signals, so the two modes teach each other. A
 * tapped tile fills solid brand colour; the short [SignalVisual] label does the reading, not the
 * long cue sentence (kept only as the screen-reader description — full context still reaches
 * TalkBack, but a sighted user scans icons, not sentences).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualCueGrid(
    cues: List<Pair<String, String>>,
    tapped: Set<String>,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((id, fullLabel) in cues) {
            val visual = signalVisualForCue(id)
            ManualCueTile(
                visual = visual,
                fullLabel = fullLabel,
                selected = id in tapped,
                onClick = { onTap(id) },
            )
        }
    }
}

@Composable
private fun ManualCueTile(visual: SignalVisual, fullLabel: String, selected: Boolean, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    val bg = if (selected) c.indigo else c.panel
    val fg = if (selected) Color.White else c.ink
    val border = if (selected) c.indigo else c.lineStrong

    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .width(94.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = fullLabel },
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(painterResource(visual.icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(
                visual.label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = fg,
            )
        }
    }
}
