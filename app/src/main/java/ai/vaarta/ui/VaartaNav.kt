package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.HistoryScreen
import ai.vaarta.SessionViewModel
import ai.vaarta.VaartaScreen
import ai.vaarta.complaint.ComplaintFlowViewModel
import ai.vaarta.conversation.ConversationViewModel
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.AwarenessCard
import ai.vaarta.feed.AwarenessViewModel
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.panic.PanicViewModel
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.StateFlow

enum class VaartaTab { HOME, HISTORY, HELP }

sealed interface SubScreen {
    data object None : SubScreen
    data object Live : SubScreen
    data object Chat : SubScreen
    data class Article(val card: AwarenessCard) : SubScreen
    data object Complaint : SubScreen
    data object Settings : SubScreen
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
    conversationVm: ConversationViewModel,
    awarenessVm: AwarenessViewModel,
    panicVm: PanicViewModel,
    complaintVm: ComplaintFlowViewModel,
    openLiveRequests: StateFlow<Int>,
    onShare: (String) -> Unit,
    onShareGeneric: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(VaartaTab.HOME) }
    var sub by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    val feed by awarenessVm.state.collectAsState()
    val density = LocalDensity.current

    // Restore (redesign spec §B3): the bubble/notification asked to jump to the live page — the
    // counter (not a boolean) so this fires on every restore, including a second one in a row.
    val openLiveSignal by openLiveRequests.collectAsState()
    LaunchedEffect(openLiveSignal) {
        if (openLiveSignal > 0) sub = SubScreen.Live
    }

    // Panic-sheet personalization context (redesign spec §A2): the live session's current read,
    // else the most recently SAVED call/recording/chat — [PanicContextSelector] picks between them.
    val liveScamType by vm.session.scamType.collectAsState()
    val liveRiskLevel by vm.session.displayedLevel.collectAsState()
    val recentCalls by historyVm.sessions.collectAsState()
    val recentScamType = recentCalls.firstOrNull()?.scamType
    val recentRiskLevel = recentCalls.firstOrNull()?.finalLevel
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
                    onShare = onShare, onOpenUrl = onOpenUrl,
                    onBack = { sub = SubScreen.None },
                )
                SubScreen.Chat -> ConversationScreen(
                    vm = conversationVm,
                    onBack = { sub = SubScreen.None; tab = VaartaTab.HISTORY },
                    onOpenUrl = onOpenUrl,
                    onShare = onShare,
                    onShareGeneric = onShareGeneric,
                    onExportPdf = onExportPdf,
                    onReport = { draft ->
                        complaintVm.open(draft = draft, scamCode = null, moneyLost = false)
                        sub = SubScreen.Complaint
                    },
                )
                is SubScreen.Article -> ArticleScreen(
                    card = currentSub.card,
                    relatedPool = feed.cards,
                    onBack = { sub = SubScreen.None; tab = VaartaTab.HOME },
                    onOpenUrl = onOpenUrl,
                    onShare = onShare,
                    onAskAbout = { seed, topic -> conversationVm.newChat(seed, topic); sub = SubScreen.Chat },
                    onOpenArticle = { card -> sub = SubScreen.Article(card) },
                )
                SubScreen.Complaint -> ComplaintFlowScreen(vm = complaintVm, onBack = { sub = SubScreen.None })
                SubScreen.Settings -> SettingsScreen(vm = vm, onBack = { sub = SubScreen.None })
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
                onAskVaarta = { conversationVm.newChat(); sub = SubScreen.Chat },
                onOpenUrl = onOpenUrl,
                feedCards = feed.cards,
                feedOrigin = feed.origin,
                feedRefreshing = feed.refreshing,
                onOpenArticle = { card -> sub = SubScreen.Article(card) },
                onRefreshFeed = { awarenessVm.refresh() },
                panicVm = panicVm,
                liveScamType = liveScamType,
                liveRiskLevel = liveRiskLevel.name,
                recentScamType = recentScamType,
                recentRiskLevel = recentRiskLevel,
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
                onOpenUrl = onOpenUrl,
                onStartLive = { sub = SubScreen.Live },
                onOpenSettings = { sub = SubScreen.Settings },
                panicVm = panicVm,
                liveScamType = liveScamType,
                liveRiskLevel = liveRiskLevel.name,
                recentScamType = recentScamType,
                recentRiskLevel = recentRiskLevel,
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
