package ai.vaarta.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a saved session came from (ADR-0004): a live in-call copilot run, or a recorded-clip analysis. */
enum class SessionSource { LIVE, RECORDING }

/** Which voice a saved turn belongs to — mirrors the app's ChatItem so history replays identically. */
enum class TurnKind { CALLER, USER, COACH }

/**
 * One saved conversation (Phase 4B, ADR-0004). Persisted only after explicit user consent; encrypted
 * at rest by SQLCipher. [endedAtMs] is null while the session is still live — turns are written as they
 * arrive (crash-safe) and the row is finalized with the final score/level on stop.
 */
@Entity(tableName = "call_session")
data class CallSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long? = null,
    @ColumnInfo(name = "final_score") val finalScore: Int = 0,
    @ColumnInfo(name = "final_level") val finalLevel: String = "OBSERVING",
    @ColumnInfo(name = "scam_type") val scamType: String? = null,
    @ColumnInfo(name = "language") val language: String? = null,
    @ColumnInfo(name = "source") val source: SessionSource = SessionSource.LIVE,
)

/**
 * One turn in a saved session's thread. Represents any of the three chat voices; [kind] selects which.
 * Coach turns carry [warning]/[repliesJson]/[scamType]/[sourcesJson]; caller/user turns carry [text].
 * Replies and sources are stored as opaque JSON so core:data need not depend on core:reasoning's model
 * types — the app serializes/deserializes them at the boundary (keeps the module graph acyclic).
 */
@Entity(
    tableName = "turn",
    foreignKeys = [
        ForeignKey(
            entity = CallSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "kind") val kind: TurnKind,
    @ColumnInfo(name = "text") val text: String = "",
    @ColumnInfo(name = "warning") val warning: String? = null,
    @ColumnInfo(name = "replies_json") val repliesJson: String? = null,
    @ColumnInfo(name = "scam_type") val scamType: String? = null,
    @ColumnInfo(name = "sources_json") val sourcesJson: String? = null,
    @ColumnInfo(name = "at_ms") val atMs: Long,
)
