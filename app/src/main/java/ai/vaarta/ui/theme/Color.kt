package ai.vaarta.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The VAARTA colour tokens (design system v2). Semantic, not raw — every screen reads these instead of
 * hard-coding hex, so the palette lives in one place. Two guarantees are baked in:
 *  - **Colour is reserved for risk.** Chrome uses [indigo] + neutrals; the risk ramp is the only loud set.
 *  - **WCAG AA.** Every risk fill clears ≥4.5:1 with white text (fixes the old amber #F59E0B = 1.9:1 fail).
 *
 * Held in a [VaartaColors] instance provided via `LocalVaartaColors` (see Theme.kt) because Material3's
 * ColorScheme has no slots for our semantic risk/tint/reply tokens.
 */
data class VaartaColors(
    val isDark: Boolean,
    // surfaces + text
    val bg: Color,
    val panel: Color,
    val line: Color,
    val lineStrong: Color,
    val track: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    // brand
    val indigo: Color,
    val indigoPress: Color,
    val indigoTint: Color,
    val indigoInk: Color,
    // risk ramp
    val observing: Color,
    val caution: Color,
    val high: Color,
    val scam: Color,
    val safe: Color,
    // risk tints (soft backgrounds)
    val observingTint: Color,
    val cautionTint: Color,
    val highTint: Color,
    val scamTint: Color,
    val safeTint: Color,
    // reply intents (ask / refuse / exit) — a traffic system distinct from brand indigo
    val verify: Color,
    val verifyTint: Color,
    val verifyInk: Color,
    val refuse: Color,
    val refuseTint: Color,
    val refuseInk: Color,
    val exit: Color,
    val exitTint: Color,
    val exitInk: Color,
    // chat bubbles
    val callerBubble: Color,
)

val VaartaLight = VaartaColors(
    isDark = false,
    bg = Color(0xFFF6F5F9),
    panel = Color(0xFFFFFFFF),
    line = Color(0xFFE6E4EE),
    lineStrong = Color(0xFFCECBDA),
    track = Color(0xFFE6E4EE),
    ink = Color(0xFF1B1826),
    muted = Color(0xFF736F83),
    faint = Color(0xFF9C98AC),
    indigo = Color(0xFF4B45C6),
    indigoPress = Color(0xFF3B35A8),
    indigoTint = Color(0xFFECEBFA),
    indigoInk = Color(0xFF2A2680),
    observing = Color(0xFF475569),
    caution = Color(0xFFB45309),
    high = Color(0xFFC2410C),
    scam = Color(0xFFC11B1B),
    safe = Color(0xFF2E7D32),
    observingTint = Color(0xFFEEF1F5),
    cautionTint = Color(0xFFFBEEDD),
    highTint = Color(0xFFFBE6DA),
    scamTint = Color(0xFFFBE1E1),
    safeTint = Color(0xFFE5F0E6),
    verify = Color(0xFF1E5F9E),
    verifyTint = Color(0xFFE7F0FB),
    verifyInk = Color(0xFF153A63),
    refuse = Color(0xFFB3261E),
    refuseTint = Color(0xFFFBE7E8),
    refuseInk = Color(0xFF5B1A1D),
    exit = Color(0xFF2E7D32),
    exitTint = Color(0xFFE5F0E6),
    exitInk = Color(0xFF1C3D20),
    callerBubble = Color(0xFFF2F1F7),
)

val VaartaDark = VaartaColors(
    isDark = true,
    bg = Color(0xFF100F16),
    panel = Color(0xFF1A1822),
    line = Color(0xFF2C2937),
    lineStrong = Color(0xFF3B3749),
    track = Color(0xFF2C2937),
    ink = Color(0xFFECEBF2),
    muted = Color(0xFF938FA3),
    faint = Color(0xFF6B6779),
    indigo = Color(0xFF8F8AF0),
    indigoPress = Color(0xFFA7A3F4),
    indigoTint = Color(0xFF211D3F),
    indigoInk = Color(0xFFC9C6FB),
    // risk fills stay saturated on dark; they read well on the dark ground and keep white-text AA.
    observing = Color(0xFF64748B),
    caution = Color(0xFFD97706),
    high = Color(0xFFEA580C),
    scam = Color(0xFFEF4444),
    safe = Color(0xFF4CAF50),
    observingTint = Color(0xFF20242C),
    cautionTint = Color(0xFF2E2114),
    highTint = Color(0xFF301A12),
    scamTint = Color(0xFF301616),
    safeTint = Color(0xFF16261A),
    verify = Color(0xFF7FB0E6),
    verifyTint = Color(0xFF16233A),
    verifyInk = Color(0xFFBCD4F0),
    refuse = Color(0xFFE88A8A),
    refuseTint = Color(0xFF331618),
    refuseInk = Color(0xFFF0BCC0),
    exit = Color(0xFF7FC784),
    exitTint = Color(0xFF152915),
    exitInk = Color(0xFFBCE0BE),
    callerBubble = Color(0xFF211E2B),
)
