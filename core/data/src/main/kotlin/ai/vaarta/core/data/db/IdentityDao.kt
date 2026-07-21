package ai.vaarta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity LIMIT 1")
    suspend fun get(): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: IdentityEntity)

    @Query("DELETE FROM identity")
    suspend fun clear()
}
