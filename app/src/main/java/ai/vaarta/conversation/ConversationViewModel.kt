package ai.vaarta.conversation

import ai.vaarta.ChatItem
import ai.vaarta.ai.GeminiClient
import ai.vaarta.core.data.HistoryRepository
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.reasoning.ChatMessage
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
 * Owns one free-form "Ask VAARTA" chat conversation (v2, spec §6.5). Like [ai.vaarta.history.HistoryViewModel]
 * it needs a Context (encrypted Room), so it's an AndroidViewModel and separate from the RAM-only
 * deterministic brain. A conversation persists to the unified store as [SessionSource.CHAT]; the row is
 * created lazily on the first send (title = first message) so an opened-but-unused chat leaves nothing.
 *
 * Chat safety = the [ai.vaarta.ai.ChatPrompt] system instruction + fail-closed (a safe fallback line),
 * NOT the coaching deny-list — see ChatPrompt for why.
 */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HistoryRepository.create(app)

    private val _turns = MutableStateFlow<List<ChatItem>>(emptyList())
    val turns: StateFlow<List<ChatItem>> = _turns.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var conversationId: Long? = null

    /** Begin a fresh blank chat. The DB row is created on the first [send]. */
    fun newChat() {
        conversationId = null
        _turns.value = emptyList()
    }

    /** Open an existing CHAT conversation to continue it. */
    fun open(id: Long) {
        conversationId = id
        viewModelScope.launch {
            val swt = repo.getSessionWithTurns(id) ?: return@launch
            _turns.value = swt.turns.toChatItems()
        }
    }

    fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || _sending.value) return
        // Show the user's message immediately; compute chat history BEFORE adding it.
        val priorHistory = _turns.value.mapNotNull { it.toChatMessage() }
        _turns.value = _turns.value + ChatItem.You(msg)
        _sending.value = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = conversationId
                ?: repo.startSession(now, SessionSource.CHAT, conversationTitleFrom(msg)).also { conversationId = it }
            persist(ChatItem.You(msg), id, now)

            val answer = withContext(Dispatchers.IO) {
                GeminiClient.chat(context = null, history = priorHistory, userText = msg)
            }
            val reply = if (answer != null) ChatItem.Assistant(answer.text, answer.sources) else ChatItem.Assistant(FALLBACK)
            _turns.value = _turns.value + reply
            persist(reply, id, System.currentTimeMillis())
            _sending.value = false
        }
    }

    private suspend fun persist(item: ChatItem, id: Long, atMs: Long) {
        listOf(item).toTurnEntities(sessionId = id, baseAtMs = atMs).forEach { repo.appendTurn(it) }
    }

    private fun ChatItem.toChatMessage(): ChatMessage? = when (this) {
        is ChatItem.You -> ChatMessage(fromUser = true, text = text)
        is ChatItem.Assistant -> ChatMessage(fromUser = false, text = text)
        else -> null // caller/coach turns aren't part of a free-form chat exchange
    }

    private companion object {
        const val FALLBACK =
            "I couldn't reach the assistant just now. Whatever the caller says, never pay money or " +
                "share an OTP or PIN. If you feel unsure or pressured, hang up and call 1930."
    }
}
