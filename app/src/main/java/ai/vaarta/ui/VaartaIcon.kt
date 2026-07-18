package ai.vaarta.ui

import ai.vaarta.ui.theme.VaartaTheme
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every VAARTA line icon is drawn through here so the tint always comes from a theme token, never a
 * raw hex — that keeps dark mode and the "colour = risk only" rule correct. The underlying vector's
 * own stroke colour is irrelevant; [Icon] overrides it with [tint].
 */
@Composable
fun VaartaIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = VaartaTheme.colors.ink,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
