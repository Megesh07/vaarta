package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.riskColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Risk Ring — the heart of the VAARTA interface (design system §1). One dial carries the score, the
 * state colour, and (at SCAM_PATTERN) a shield-x glyph, so risk is legible *before a word is read* and
 * in any language. Deliberately reused at every scale — 150dp hero, 88dp analyzer, 52dp overlay bubble —
 * so the visual language is identical everywhere. The arc animates as the score climbs; at the top state
 * it breathes, so the interface itself gets tense.
 *
 * Score is intentionally secondary to colour (MOBILE_UX_SPEC §2): shown for large rings, dropped for the
 * tiny overlay where the colour + glyph alone carry the message. The whole thing is one semantics node
 * announcing the plain-language state for TalkBack.
 */
@Composable
fun RiskRing(
    level: RiskLevel,
    score: Int,
    stateText: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 150.dp,
    stroke: Dp = 12.dp,
    showScore: Boolean = true,
    ringColorOverride: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = VaartaTheme.colors
    val ringColor = ringColorOverride ?: colors.riskColor(level)
    val isScam = level == RiskLevel.SCAM_PATTERN && ringColorOverride == null
    val trackColor = if (isScam) ringColor.copy(alpha = 0.16f) else colors.track

    val target = (score.coerceIn(0, 100)) / 100f
    val sweep by animateFloatAsState(targetValue = target, animationSpec = tween(650), label = "ringSweep")

    // Breathe only at the top state (respects the "no flashing" rule — a slow 1.2s scale, not a blink).
    val scale = if (isScam) {
        val inf = rememberInfiniteTransition(label = "pulse")
        inf.animateFloat(
            initialValue = 1f, targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulseScale",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(ringSize)
            .semantics { contentDescription = "$stateText, risk $score of 100" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ringSize).graphicsLayer { scaleX = scale; scaleY = scale }) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arc = Size(size.width - sw, size.height - sw)
            val tl = Offset(inset, inset)
            drawArc(trackColor, -90f, 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (sweep > 0f) {
                drawArc(ringColor, -90f, sweep * 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        val big = ringSize >= 96.dp
        when {
            isScam -> Icon(
                painterResource(R.drawable.ic_shield_x), contentDescription = null,
                tint = ringColor, modifier = Modifier.size(ringSize * 0.30f),
            )
            showScore && big -> Text(
                "$score",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = (ringSize.value * 0.28f).sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }
    }
}
