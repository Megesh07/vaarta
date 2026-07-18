package ai.vaarta.ui

import ai.vaarta.core.common.Stage
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.riskColor
import ai.vaarta.ui.theme.stateLabel
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Live-screen hero (design system §1/§2): the [RiskRing] + a plain-language state line + a quiet row
 * of detected-signal tokens. This replaces the old text banner — the ring and the icons do the work a
 * paragraph used to, so it reads in ≤2s and in any language. Reassurance (cited consensus that the call
 * is genuine) turns the ring green; otherwise the risk ramp drives it.
 *
 * [idleLabel] overrides the computed state line and hides the score for the true at-rest state
 * (redesign spec §6.3) — "Listening & checking" + a big "0" was a lie before anything had happened.
 * [liveBadge] shows a small pulsing dot beside the state line during an active call, replacing the
 * old raw "● Live: CONNECTING" header text.
 */
@Composable
fun RiskHero(
    level: RiskLevel,
    score: Int,
    reassure: Boolean,
    aiRaised: Boolean,
    detectedStages: List<Stage>,
    modifier: Modifier = Modifier,
    idleLabel: String? = null,
    liveBadge: Boolean = false,
) {
    val colors = VaartaTheme.colors
    val stateText = idleLabel ?: if (reassure) stringResource(ai.vaarta.R.string.risk_reassure_genuine) else stateLabel(level)
    val stateColor = if (idleLabel != null) colors.muted else if (reassure) colors.safe else colors.riskColor(level)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RiskRing(
            level = level,
            score = score,
            stateText = stateText,
            showScore = idleLabel == null,
            ringColorOverride = if (reassure) colors.safe else null,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stateText,
                style = MaterialTheme.typography.headlineSmall,
                color = stateColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (liveBadge) LivePulseDot()
        }
        if (aiRaised && !reassure) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VaartaIcon(ai.vaarta.R.drawable.ic_alert_triangle, contentDescription = null, tint = colors.muted, size = 15.dp)
                Text(stringResource(ai.vaarta.R.string.risk_flagged_web), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }

        // Detected-signal tokens: one per distinct stage in play (excluding NONE), newest scam-script
        // stages first. Filled colour = seen this call. The glyphs carry the meaning; labels stay short.
        val stages = detectedStages.filter { it != Stage.NONE }.distinct().sortedByDescending { it.ordinal }.take(4)
        if (stages.isNotEmpty() && !reassure) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (stage in stages) SignalToken(stage, stateColor, colors.riskColor(level).copy(alpha = if (colors.isDark) 0.18f else 0.10f))
            }
        }
    }
}

/** A small breathing dot marking an active call — replaces the raw "● Live: CONNECTING" header text. */
@Composable
private fun LivePulseDot() {
    val colors = VaartaTheme.colors
    val inf = rememberInfiniteTransition(label = "livePulse")
    val alpha by inf.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveAlpha",
    )
    Surface(color = colors.indigo.copy(alpha = alpha), shape = CircleShape, modifier = Modifier.size(8.dp)) {}
}

@Composable
private fun SignalToken(stage: Stage, content: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    val v = signalVisualForStage(stage)
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Row(
            Modifier.padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(v.icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(v.label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
