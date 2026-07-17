package ai.vaarta.ui.components

import ai.vaarta.ui.theme.VSpace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The one sub-screen frame (spec §8.1–§8.3). Every full-screen sub-surface renders inside this, so
 * a screen *cannot* forget the status bar, the back affordance, or system-back handling — the
 * Article-under-the-clock and back-exits-the-app bugs become impossible by construction.
 */
@Composable
fun VaartaSubScreen(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    spacing: Dp = VSpace.md,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack) // system back == the back arrow, never app exit (spec §8.2)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            VaartaBackBar(title = title, onBack = onBack)
            if (scrollable) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VSpace.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) { content() }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
            if (bottomContent != null) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = VSpace.xl, vertical = VSpace.md),
                    verticalArrangement = Arrangement.spacedBy(VSpace.sm),
                ) { bottomContent() }
            }
        }
    }
}
