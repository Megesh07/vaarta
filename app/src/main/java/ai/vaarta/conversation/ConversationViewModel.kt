package ai.vaarta.conversation

import ai.vaarta.ChatItem
import ai.vaarta.ai.ChatAttachment
import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.data.HistoryRepository
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.reasoning.ChatMessage
import ai.vaarta.core.reasoning.RiskLevel
import ai.vaarta.core.reasoning.conversationTitleFrom
import ai.vaarta.history.toChatItems
import ai.vaarta.history.toTurnEntities
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns one conversation (v2, spec §4.4/§6.5). A blank "Ask VAARTA" chat, or an opened saved call /
 * recording — the same screen either way. For a call/recording it exposes a [header] (verdict) and
 * feeds the call's transcript to the model as context so the user can "ask about this call"; the
 * follow-up turns persist to the SAME conversation. A blank chat has no header and is created lazily
 * on first send (title = first message).
 *
 * Chat safety = the [ai.vaarta.ai.ChatPrompt] system instruction + fail-closed — NOT the coaching
 * deny-list (see ChatPrompt for why).
 */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HistoryRepository.create(app)

    /** Verdict shown above the thread for a saved call/recording (null for a free-form chat). */
    data class CallHeader(val level: RiskLevel, val score: Int, val scamType: String?, val kind: SessionSource)

    private val _turns = MutableStateFlow<List<ChatItem>>(emptyList())
    val turns: StateFlow<List<ChatItem>> = _turns.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _header = MutableStateFlow<CallHeader?>(null)
    val header: StateFlow<CallHeader?> = _header.asStateFlow()

    private var conversationId: Long? = null
    private var contextText: String? = null

    /**
     * Begin a fresh blank chat. The DB row is created on the first [send]. [seedContext] optionally
     * grounds the chat in a topic the user tapped "Ask about this" on (e.g. an awareness article) —
     * it's fed to the model as untrusted context, exactly like a saved call's transcript, but shows
     * no verdict header.
     */
    fun newChat(seedContext: String? = null) {
        conversationId = null
        contextText = seedContext?.takeIf { it.isNotBlank() }
        _turns.value = emptyList()
        _header.value = null
    }

    /** Open an existing conversation — a chat to continue, or a saved call/recording to ask about. */
    fun open(id: Long) {
        conversationId = id
        viewModelScope.launch {
            val swt = repo.getSessionWithTurns(id) ?: return@launch
            val items = swt.turns.toChatItems()
            _turns.value = items
            val s = swt.session
            if (s.source != SessionSource.CHAT) {
                _header.value = CallHeader(levelFromName(s.finalLevel), s.finalScore, s.scamType, s.source)
                contextText = buildContext(s.source, s.finalLevel, s.finalScore, s.scamType, items)
            } else {
                _header.value = null
                contextText = null
            }
        }
    }

    fun send(text: String, attachments: List<ChatAttachment> = emptyList()) {
        val msg = text.trim()
        if ((msg.isEmpty() && attachments.isEmpty()) || _sending.value) return
        val youText = buildString {
            if (msg.isNotEmpty()) append(msg)
            for (a in attachments) {
                if (isNotEmpty()) append("\n")
                append("[${a.label}]")
            }
        }
        val priorHistory = _turns.value.mapNotNull { it.toChatMessage() }
        val ctx = contextText
        _turns.value = _turns.value + ChatItem.You(youText)
        _sending.value = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = conversationId
                ?: repo.startSession(now, SessionSource.CHAT, conversationTitleFrom(youText)).also { conversationId = it }
            persist(ChatItem.You(youText), id, now)

            val answer = withContext(Dispatchers.IO) {
                GeminiClient.chat(context = ctx, history = priorHistory, userText = msg, attachments = attachments)
            }
            val reply = if (answer != null) ChatItem.Assistant(answer.text, answer.sources) else ChatItem.Assistant(FALLBACK)
            _turns.value = _turns.value + reply
            persist(reply, id, System.currentTimeMillis())
            _sending.value = false
        }
    }

    /** Plain-text transcript + verdict for export/share (Download). */
    fun transcriptText(): String = buildString {
        _header.value?.let { h ->
            append("VAARTA — ${kindLabel(h.kind)}\n")
            append("Verdict: ${h.level.name}, risk ${h.score}/100")
            h.scamType?.let { append(", $it") }
            append("\n\n")
        }
        for (item in _turns.value) {
            when (item) {
                is ChatItem.Caller -> append("Caller: ${item.text}\n")
                is ChatItem.You -> append("Me: ${item.text}\n")
                is ChatItem.Coach -> {
                    if (item.warning.isNotBlank()) append("VAARTA (warning): ${item.warning}\n")
                    item.replies.firstOrNull()?.let { append("VAARTA (say this): ${it.text}\n") }
                }
                is ChatItem.Assistant -> append("VAARTA: ${item.text}\n")
            }
        }
        append("\nReport scams: call 1930 or cybercrime.gov.in")
    }

    private suspend fun persist(item: ChatItem, id: Long, atMs: Long) {
        listOf(item).toTurnEntities(sessionId = id, baseAtMs = atMs).forEach { repo.appendTurn(it) }
    }

    private fun ChatItem.toChatMessage(): ChatMessage? = when (this) {
        is ChatItem.You -> ChatMessage(fromUser = true, text = text)
        is ChatItem.Assistant -> ChatMessage(fromUser = false, text = text)
        else -> null // caller/coach turns are in the context summary, not the chat exchange
    }

    private fun buildContext(source: SessionSource, level: String, score: Int, scamType: String?, items: List<ChatItem>): String {
        val transcript = items.joinToString("\n") { item ->
            when (item) {
                is ChatItem.Caller -> "Caller: ${item.text}"
                is ChatItem.You -> "Me: ${item.text}"
                is ChatItem.Coach -> "VAARTA advised: ${item.warning}"
                is ChatItem.Assistant -> "VAARTA: ${item.text}"
            }
        }
        return "This is a saved ${kindLabel(source)}. VAARTA's verdict was $level (risk $score/100)" +
            (scamType?.let { ", identified as \"$it\"" } ?: "") +
            ".\nTranscript:\n$transcript"
    }

    private fun kindLabel(source: SessionSource): String = when (source) {
        SessionSource.LIVE -> "live call"
        SessionSource.RECORDING -> "call recording"
        SessionSource.CHAT -> "chat"
    }

    private fun levelFromName(name: String): RiskLevel =
        runCatching { RiskLevel.valueOf(name) }.getOrDefault(RiskLevel.OBSERVING)

    private companion object {
        const val FALLBACK =
            "I couldn't reach the assistant just now. Whatever the caller says, never pay money or " +
                "share an OTP or PIN. If you feel unsure or pressured, hang up and call 1930."
    }
}
