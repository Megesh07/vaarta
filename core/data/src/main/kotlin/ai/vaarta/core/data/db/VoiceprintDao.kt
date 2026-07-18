package ai.vaarta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface VoiceprintDao {

    @Insert
    suspend fun insertSample(sample: VoiceSampleEntity): Long

    @Query("SELECT * FROM voice_sample ORDER BY captured_at_ms ASC")
    suspend fun getAllSamples(): List<VoiceSampleEntity>

    @Query("SELECT COUNT(*) FROM voice_sample")
    suspend fun sampleCount(): Int

    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM voice_sample")
    suspend fun totalDurationMs(): Long

    /** "Clear voice data" (spec §6.5) — the one privacy control this feature adds. */
    @Query("DELETE FROM voice_sample")
    suspend fun deleteAll()
}
