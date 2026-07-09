package ai.vaarta.core.data

import ai.vaarta.core.data.db.CallSessionEntity
import ai.vaarta.core.data.db.HistoryDao
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.core.data.db.SessionWithTurns
import ai.vaarta.core.data.db.TurnEntity
import ai.vaarta.core.data.db.VaartaDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point to the encrypted saved history (Phase 4B, ADR-0004). Turns are written live as
 * they arrive so a mid-call crash never loses the thread; the session row is finalized on stop with
 * the final score/level. All persistence is opt-in (the caller only invokes [startSession] when the
 * user has consented to save) and encrypted at rest.
 */
class HistoryRepository private constructor(
    private val dao: HistoryDao,
) {
    /** Newest-first stream of saved sessions for the home history list. */
    fun observeSessions(): Flow<List<CallSessionEntity>> = dao.observeSessions()

    /** Opens a new session row and returns its id; caller appends turns and finalizes on stop. */
    suspend fun startSession(startedAtMs: Long, source: SessionSource): Long =
        dao.insertSession(CallSessionEntity(startedAtMs = startedAtMs, source = source))

    suspend fun appendTurn(turn: TurnEntity): Long = dao.insertTurn(turn)

    suspend fun finalizeSession(
        sessionId: Long,
        endedAtMs: Long,
        finalScore: Int,
        finalLevel: String,
        scamType: String?,
        language: String?,
    ) {
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(
            session.copy(
                endedAtMs = endedAtMs,
                finalScore = finalScore,
                finalLevel = finalLevel,
                scamType = scamType,
                language = language,
            ),
        )
    }

    suspend fun getSessionWithTurns(sessionId: Long): SessionWithTurns? {
        val session = dao.getSession(sessionId) ?: return null
        return SessionWithTurns(session, dao.getTurns(sessionId))
    }

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun deleteAll() = dao.deleteAllSessions()

    /** Retention sweep — call on launch; deletes sessions started more than [retentionDays] ago. */
    suspend fun applyRetention(nowMs: Long, retentionDays: Int) {
        if (retentionDays <= 0) return // 0 / negative = keep forever
        dao.deleteSessionsBefore(nowMs - retentionDays * DAY_MS)
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun create(context: Context): HistoryRepository {
            val db = VaartaDatabase.get(context)
            return HistoryRepository(db.historyDao())
        }
    }
}
