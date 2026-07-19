package ai.vaarta.ui

import ai.vaarta.AnalyzeScreen
import ai.vaarta.R
import ai.vaarta.HistoryScreen
import ai.vaarta.SessionViewModel
import ai.vaarta.VaartaScreen
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.feed.AwarenessViewModel
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.recording.AudioAnalyzerViewModel
import ai.vaarta.ui.theme.VaartaTheme
import ai.vaarta.ui.theme.isReducedMotionEnabled
import ai.vaarta.ui.theme.motionDurationMs
import ai.vaarta.ui.theme.vaartaPressable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class VaartaTab { HOME, HISTORY, HELP }

sealed interface SubScreen {
    data object None : SubScreen
    data object Live : SubScreen
    data object Analyze : SubScreen
    data object Chat : SubScreen
    data class Article(val card: AwarenessCard) : SubScreen
}

/**
 * Top-level host. Three bottom tabs (Home/History/Help). Full-screen "sub-screens" (the live
 * copilot, the recording analyzer, a saved-call detail) render over the tabs with their own back
 * action and hide the bottom bar while open. No navigation-compose dependency — a small state
 * holder is enough for this shape and keeps the build $0/lean.
 */
@Composable
fun VaartaNav(
    vm: SessionViewModel,
    historyVm: HistoryViewModel,
    analyzerVm: AudioAnalyzerViewModel,
    conversationVm: ConversationViewModel,
    awarenessVm: AwarenessViewModel,
    onShare: (String) -> Unit,
    onShareGeneric: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(VaartaTab.HOME) }
    var sub by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    val feed by awarenessVm.state.collectAsState()
    val density = LocalDensity.current
    val slideUpPx = with(density) { 8.dp.roundToPx() }
    val reducedMotion = isReducedMotionEnabled(LocalContext.current)

    // A sub-screen takes the whole window (no bottom bar) so nothing competes with its back action.
    if (sub != SubScreen.None) {
        AnimatedContent(
            targetState = sub,
            transitionSpec = {
                val enterMs = motionDurationMs(220, reducedMotion)
                val exitMs = motionDurationMs(110, reducedMotion)
                (fadeIn(tween(enterMs)) + slideInVertically(tween(enterMs)) { slideUpPx }) togetherWith
                    (fadeOut(tween(exitMs)) + slideOutVertically(tween(exitMs)) { slideUpPx })
            },
            label = "subScreen",
        ) { currentSub ->
            when (currentSub) {
                SubScreen.Live -> VaartaScreen(
                    vm = vm, historyVm = historyVm,
                    onOpenHistory = { sub = SubScreen.None; tab = VaartaTab.HISTORY },
                    onShare = onShare, onExportPdf = onExportPdf, onOpenUrl = onOpenUrl,
                    onBack = { sub = SubScreen.None },
                )
                SubScreen.Analyze -> AnalyzeScreen(
                    analyzerVm = analyzerVm, historyVm = historyVm,
                    onBack = { analyzerVm.reset(); sub = SubScreen.None },
                    onShare = onShare, onOpenUrl = onOpenUrl,
                )
                SubScreen.Chat -> ConversationScreen(
                    vm = conversationVm,
                    onBack = { sub = SubScreen.None; tab = VaartaTab.HISTORY },
                    onOpenUrl = onOpenUrl,
                    onShare = onShare,
                    onShareGeneric = onShareGeneric,
                )
                is SubScreen.Article -> ArticleScreen(
                    card = currentSub.card,
                    onBack = { sub = SubScreen.None; tab = VaartaTab.HOME },
                    onOpenUrl = onOpenUrl,
                    onShare = onShare,
                    onAskAbout = { seed -> conversationVm.newChat(seed); sub = SubScreen.Chat },
                )
                SubScreen.None -> Unit
            }
        }
        return
    }

    Scaffold(
        bottomBar = { VaartaBottomNav(tab, onSelect = { tab = it }) },
    ) { pad ->
        when (tab) {
            VaartaTab.HOME -> HomeScreen(
                aiConfigured = vm.session.aiConfigured,
                onStartLive = { sub = SubScreen.Live },
                onAnalyzeRecording = { sub = SubScreen.Analyze },
                onAskVaarta = { conversationVm.newChat(); sub = SubScreen.Chat },
                onOpenUrl = onOpenUrl,
                feedCards = feed.cards,
                feedOrigin = feed.origin,
                feedRefreshing = feed.refreshing,
                onOpenArticle = { card -> sub = SubScreen.Article(card) },
                modifier = Modifier.padding(pad),
            )
            VaartaTab.HISTORY -> HistoryScreen(
                historyVm = historyVm,
                onNewChat = { conversationVm.newChat(); sub = SubScreen.Chat },
                onOpen = { session -> conversationVm.open(session.id); sub = SubScreen.Chat },
                modifier = Modifier.padding(pad),
            )
            VaartaTab.HELP -> HelpScreen(
                vm = vm,
                onShare = onShare,
                onShareGeneric = onShareGeneric,
                onExportPdf = onExportPdf,
                onOpenUrl = onOpenUrl,
                onStartLive = { sub = SubScreen.Live },
                modifier = Modifier.padding(pad),
            )
        }
    }
}

/**
 * Custom quiet bottom nav (redesign spec §6.8) — panel bg + top hairline, no tonal pill; the
 * active item is just an indigo icon + label + a 3dp dot. A restyle only, not a navigation change.
 */
@Composable
private fun VaartaBottomNav(tab: VaartaTab, onSelect: (VaartaTab) -> Unit) {
    val c = VaartaTheme.colors
    Surface(color = c.panel) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = c.line)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(R.drawable.ic_nav_shield, stringResource(R.string.nav_home), tab == VaartaTab.HOME) { onSelect(VaartaTab.HOME) }
                NavItem(R.drawable.ic_nav_chat, stringResource(R.string.nav_chats), tab == VaartaTab.HISTORY) { onSelect(VaartaTab.HISTORY) }
                NavItem(R.drawable.ic_nav_help, stringResource(R.string.nav_help), tab == VaartaTab.HELP) { onSelect(VaartaTab.HELP) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    val tint = if (selected) c.indigo else c.faint
    Column(
        Modifier.weight(1f).fillMaxHeight().vaartaPressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaartaIcon(icon, contentDescription = null, tint = tint, size = 24.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Surface(
            color = if (selected) c.indigo else Color.Transparent,
            shape = CircleShape,
            modifier = Modifier.size(3.dp),
        ) {}
    }
}
