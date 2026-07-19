package ai.vaarta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GuardianDao {

    @Query("SELECT * FROM guardian LIMIT 1")
    suspend fun get(): GuardianEntity?

    /** Upsert: [GuardianEntity.id] is always 1, so REPLACE naturally gives set/update semantics
     *  with one query — there is never more than one guardian row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: GuardianEntity)

    @Query("DELETE FROM guardian")
    suspend fun clear()
}
