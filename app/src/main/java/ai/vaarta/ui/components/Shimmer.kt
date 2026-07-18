package ai.vaarta.ui.components

import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholder lines (redesign spec §8.5): content-shaped, gently pulsing — loading looks
 * like the page it becomes, never a spinner next to a void. Pure alpha pulse, no flashing.
 */
@Composable
fun ShimmerLines(lines: Int, modifier: Modifier = Modifier) {
    val c = VaartaTheme.colors
    val pulse by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(modifier.fillMaxWidth().alpha(pulse), verticalArrangement = Arrangement.spacedBy(VSpace.md)) {
        repeat(lines) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 3 == 2) 0.6f else 1f)
                    .height(14.dp)
                    .background(c.track, RoundedCornerShape(7.dp)),
            )
        }
    }
}
