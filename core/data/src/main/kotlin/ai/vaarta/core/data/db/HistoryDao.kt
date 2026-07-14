package ai.vaarta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A session with its turns, for the history detail screen. */
data class SessionWithTurns(
    val session: CallSessionEntity,
    val turns: List<TurnEntity>,
)

@Dao
interface HistoryDao {

    @Insert
    suspend fun insertSession(session: CallSessionEntity): Long

    @Update
    suspend fun updateSession(session: CallSessionEntity)

    @Insert
    suspend fun insertTurn(turn: TurnEntity): Long

    /** Newest first — the home history list. */
    @Query("SELECT * FROM call_session ORDER BY started_at_ms DESC")
    fun observeSessions(): Flow<List<CallSessionEntity>>

    @Query("SELECT * FROM call_session WHERE id = :id")
    suspend fun getSession(id: Long): CallSessionEntity?

    @Query("UPDATE call_session SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    @Query("SELECT * FROM turn WHERE session_id = :sessionId ORDER BY at_ms ASC, id ASC")
    suspend fun getTurns(sessionId: Long): List<TurnEntity>

    @Query("DELETE FROM call_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM call_session")
    suspend fun deleteAllSessions()

    /** Retention sweep — drop sessions older than the cutoff (cascade removes their turns). */
    @Query("DELETE FROM call_session WHERE started_at_ms < :cutoffMs")
    suspend fun deleteSessionsBefore(cutoffMs: Long)
}
