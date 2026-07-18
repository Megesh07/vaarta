package ai.vaarta.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext

/**
 * True when the system "Remove animations" setting is on — every motion in the app checks this.
 * Reads the animator duration scale via [Settings.Global] rather than the API-33+
 * `ValueAnimator.getDurationScale()` convenience, since minSdk here is 29.
 */
fun isReducedMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Duration for a motion, collapsed to 0 under reduced-motion (redesign spec §9). */
fun motionDurationMs(full: Int, reducedMotion: Boolean): Int = if (reducedMotion) 0 else full

/**
 * The one press treatment for every tappable row/tile (redesign spec §9): 0.98 scale is the tonal
 * feedback itself, so the stock ripple is switched off to avoid a double effect. Skipped entirely
 * under reduced-motion — the click still fires, it just doesn't animate.
 */
fun Modifier.vaartaPressable(onClick: () -> Unit, enabled: Boolean = true): Modifier = composed {
    val reducedMotion = isReducedMotionEnabled(LocalContext.current)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed && !reducedMotion) 0.98f else 1f
    val scale by animateFloatAsState(target, label = "vaartaPress")
    this.scale(scale).clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}
