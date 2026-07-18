package ai.vaarta.history

import ai.vaarta.ChatItem
import ai.vaarta.core.data.db.TurnEntity
import ai.vaarta.core.data.db.TurnKind
import ai.vaarta.core.reasoning.Reply
import ai.vaarta.core.reasoning.ReplyKind
import ai.vaarta.core.reasoning.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps the in-RAM chat thread ([ChatItem]) to/from persisted [TurnEntity] rows (Phase 4B, ADR-0004).
 *
 * core:data stays free of core:reasoning's model types — a Coach turn's replies and sources are stored
 * as opaque JSON strings and (de)serialized here, at the app boundary, via these small DTOs. That keeps
 * the module graph acyclic (core:data → core:common only) while a saved thread replays identically.
 */
private val json = Json { ignoreUnknownKeys = true }

@Serializable private data class ReplyDto(val text: String, val kind: String)
@Serializable private data class SourceDto(val title: String, val uri: String)

fun List<ChatItem>.toTurnEntities(sessionId: Long, baseAtMs: Long): List<TurnEntity> =
    mapIndexed { i, item ->
        // Preserve chronological order; the live thread carries no per-item timestamp, so index the
        // save time monotonically (getTurns orders by at_ms then id — stable either way).
        val atMs = baseAtMs + i
        when (item) {
            is ChatItem.Caller -> TurnEntity(sessionId = sessionId, kind = TurnKind.CALLER, text = item.text, atMs = atMs)
            is ChatItem.You -> TurnEntity(sessionId = sessionId, kind = TurnKind.USER, text = item.text, atMs = atMs)
            is ChatItem.Coach -> TurnEntity(
                sessionId = sessionId,
                kind = TurnKind.COACH,
                warning = item.warning,
                repliesJson = json.encodeToString(item.replies.map { ReplyDto(it.text, it.kind.name) }),
                scamType = item.scamType,
                sourcesJson = json.encodeToString(item.sources.map { SourceDto(it.title, it.uri) }),
                atMs = atMs,
            )
            is ChatItem.Assistant -> TurnEntity(
                sessionId = sessionId,
                kind = TurnKind.ASSISTANT,
                text = item.text,
                sourcesJson = json.encodeToString(item.sources.map { SourceDto(it.title, it.uri) }),
                atMs = atMs,
            )
        }
    }

fun List<TurnEntity>.toChatItems(): List<ChatItem> = map { turn ->
    when (turn.kind) {
        TurnKind.CALLER -> ChatItem.Caller(turn.text)
        TurnKind.USER -> ChatItem.You(turn.text)
        TurnKind.COACH -> ChatItem.Coach(
            warning = turn.warning.orEmpty(),
            replies = turn.repliesJson?.let { raw ->
                runCatching { json.decodeFromString<List<ReplyDto>>(raw) }.getOrDefault(emptyList())
                    .map { Reply(it.text, runCatching { ReplyKind.valueOf(it.kind) }.getOrDefault(ReplyKind.VERIFY)) }
            }.orEmpty(),
            scamType = turn.scamType,
            sources = turn.sourcesJson?.let { raw ->
                runCatching { json.decodeFromString<List<SourceDto>>(raw) }.getOrDefault(emptyList())
                    .map { Source(it.title, it.uri) }
            }.orEmpty(),
        )
        TurnKind.ASSISTANT -> ChatItem.Assistant(
            text = turn.text,
            sources = turn.sourcesJson?.let { raw ->
                runCatching { json.decodeFromString<List<SourceDto>>(raw) }.getOrDefault(emptyList())
                    .map { Source(it.title, it.uri) }
            }.orEmpty(),
        )
    }
}
