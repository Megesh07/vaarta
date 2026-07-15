package ai.vaarta.ui

import ai.vaarta.AnalyzeScreen
import ai.vaarta.HistoryScreen
import ai.vaarta.SessionViewModel
import ai.vaarta.VaartaScreen
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.feed.AwarenessViewModel
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.recording.AudioAnalyzerViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(VaartaTab.HOME) }
    var sub by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    val feed by awarenessVm.state.collectAsState()

    // A sub-screen takes the whole window (no bottom bar) so nothing competes with its back action.
    if (sub != SubScreen.None) {
        when (sub) {
            SubScreen.Live -> VaartaScreen(
                vm = vm, historyVm = historyVm, analyzerVm = analyzerVm,
                onOpenHistory = { sub = SubScreen.None; tab = VaartaTab.HISTORY },
                onOpenAnalyze = { sub = SubScreen.Analyze },
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
            )
            is SubScreen.Article -> ArticleScreen(
                card = (sub as SubScreen.Article).card,
                onBack = { sub = SubScreen.None; tab = VaartaTab.HOME },
                onOpenUrl = onOpenUrl,
                onShare = onShare,
                onAskAbout = { seed -> conversationVm.newChat(seed); sub = SubScreen.Chat },
            )
            SubScreen.None -> Unit
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == VaartaTab.HOME,
                    onClick = { tab = VaartaTab.HOME },
                    icon = { Text("🛡️") },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == VaartaTab.HISTORY,
                    onClick = { tab = VaartaTab.HISTORY },
                    icon = { Text("💬") },
                    label = { Text("Conversations") },
                )
                NavigationBarItem(
                    selected = tab == VaartaTab.HELP,
                    onClick = { tab = VaartaTab.HELP },
                    icon = { Text("🆘") },
                    label = { Text("Help") },
                )
            }
        },
    ) { pad ->
        when (tab) {
            VaartaTab.HOME -> HomeScreen(
                aiConfigured = vm.session.aiConfigured,
                onStartLive = { sub = SubScreen.Live },
                onAnalyzeRecording = { sub = SubScreen.Analyze },
                onAskVaarta = { conversationVm.newChat(); sub = SubScreen.Chat },
                onOpenUrl = onOpenUrl,
                feedCards = feed.cards,
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
                onExportPdf = onExportPdf,
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(pad),
            )
        }
    }
}
