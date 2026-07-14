package ai.vaarta.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.annotation.FontRes
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * A single [Font] backed by a hand-built script-fallback [Typeface] (design system §5, the pan-Indic
 * follow-up): [resIds] first entry is the primary script, every entry after it is appended via
 * [Typeface.CustomFallbackBuilder] — Latin text draws from the bundled Noto Sans cut, Devanagari/
 * Tamil/Kannada/Telugu/Malayalam glyphs each draw from their own bundled Noto Sans cut. Pinned
 * everywhere, rather than leaning on whatever an OEM substitutes as the system fallback for those
 * scripts (Android already renders them via system fallback today — this only fixes the *cut*).
 *
 * [AndroidFont.TypefaceLoader] is Compose's documented public extension point for exactly this case:
 * none of the built-in `Font(...)` factories (path/File/ParcelFileDescriptor) can construct a
 * multi-script fallback chain, so a small custom loader is the correct, non-hacky way in — not a
 * reflection trick. Requires API 29 for [Typeface.CustomFallbackBuilder], which is this app's minSdk.
 */
@RequiresApi(29)
private class VaartaFallbackFont(@FontRes val resIds: List<Int>) :
    AndroidFont(FontLoadingStrategy.Blocking, VaartaFallbackFontLoader, FontVariation.Settings()) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}

@RequiresApi(29)
private object VaartaFallbackFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val resIds = (font as VaartaFallbackFont).resIds
        val families = resIds.map { resId ->
            PlatformFontFamily.Builder(PlatformFont.Builder(context.resources, resId).build()).build()
        }
        val builder = Typeface.CustomFallbackBuilder(families.first())
        families.drop(1).forEach(builder::addCustomFallback)
        return builder.build()
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}

/** The one VAARTA type family: bundled Latin Noto Sans + the pan-Indic scripts as pinned fallback. */
@RequiresApi(29)
fun vaartaFallbackFont(@FontRes vararg resIds: Int): Font = VaartaFallbackFont(resIds.toList())
