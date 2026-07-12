package ai.vaarta.ui.theme

import ai.vaarta.core.reasoning.RiskLevel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Access the semantic VAARTA tokens anywhere: `VaartaTheme.colors.scam`. */
val LocalVaartaColors = staticCompositionLocalOf { VaartaLight }

object VaartaTheme {
    val colors: VaartaColors
        @Composable @ReadOnlyComposable get() = LocalVaartaColors.current
}

/** Soft, calm radii (design system §5): larger on cards/bubbles, smaller on chips. */
private val VaartaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Wraps the app in the VAARTA design system: the semantic [VaartaColors] via [LocalVaartaColors], a
 * Material3 [androidx.compose.material3.ColorScheme] so stock Material components (Button, Switch, …)
 * inherit the brand, the [VaartaShapes], and [VaartaType]. Follows the OS light/dark preference.
 */
@Composable
fun VaartaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (dark) VaartaDark else VaartaLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.indigo, onPrimary = Color(0xFF14121F), primaryContainer = c.indigoTint, onPrimaryContainer = c.indigoInk,
            background = c.bg, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
            surfaceVariant = c.line, onSurfaceVariant = c.muted, outline = c.lineStrong, error = c.scam, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = c.indigo, onPrimary = Color.White, primaryContainer = c.indigoTint, onPrimaryContainer = c.indigoInk,
            background = c.bg, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
            surfaceVariant = c.line, onSurfaceVariant = c.muted, outline = c.lineStrong, error = c.scam, onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalVaartaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = VaartaType, shapes = VaartaShapes, content = content)
    }
}

/** The risk-ramp fill for a level — the one place level→colour is decided (design system §4). */
fun VaartaColors.riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.OBSERVING -> observing
    RiskLevel.CAUTION -> caution
    RiskLevel.HIGH_RISK -> high
    RiskLevel.SCAM_PATTERN -> scam
}

/** The soft tint behind a level. */
fun VaartaColors.riskTint(level: RiskLevel): Color = when (level) {
    RiskLevel.OBSERVING -> observingTint
    RiskLevel.CAUTION -> cautionTint
    RiskLevel.HIGH_RISK -> highTint
    RiskLevel.SCAM_PATTERN -> scamTint
}

/** Plain-language state line, ≤2s glanceable (MOBILE_UX_SPEC §2). */
fun stateLabel(level: RiskLevel): String = when (level) {
    RiskLevel.OBSERVING -> "Listening & checking"
    RiskLevel.CAUTION -> "Some warning signs"
    RiskLevel.HIGH_RISK -> "Strong scam signs"
    RiskLevel.SCAM_PATTERN -> "This matches a known scam"
}
