package ai.vaarta.ui.theme

import ai.vaarta.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * VAARTA type scale (design system §5): large, quiet, few weights — Apple-style deference.
 *
 * The pan-Indic type family (design system §0): bundled Noto Sans for Latin, with Devanagari (Hindi/
 * Marathi), Tamil, Kannada, Telugu, and Malayalam pinned as script fallback via
 * [vaartaFallbackFont] — one look across every language a caller might use, instead of whatever an
 * OEM happens to substitute for those scripts. minSdk is 29, matching the API this requires.
 */
private val appFont = FontFamily(
    vaartaFallbackFont(
        R.font.noto_sans,
        R.font.noto_sans_devanagari,
        R.font.noto_sans_tamil,
        R.font.noto_sans_kannada,
        R.font.noto_sans_telugu,
        R.font.noto_sans_malayalam,
    ),
)

val VaartaType = Typography(
    // Ring number / big display
    displayLarge = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    // Screen titles
    headlineMedium = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    // Risk state line
    headlineSmall = TextStyle(fontFamily = appFont, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    // Section titles
    titleLarge = TextStyle(fontFamily = appFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = appFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    // Body
    bodyLarge = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    // Labels / eyebrows
    labelLarge = TextStyle(fontFamily = appFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.2.sp),
)
