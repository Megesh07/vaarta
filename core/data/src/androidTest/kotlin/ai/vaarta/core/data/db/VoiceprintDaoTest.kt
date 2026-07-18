package ai.vaarta.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceprintDaoTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        VaartaDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Test
    fun insertAndAggregate() = runBlocking {
        val database = db()
        val dao = database.voiceprintDao()
        dao.insertSample(VoiceSampleEntity(embedding = ByteArray(8), durationMs = 4_000, capturedAtMs = 1_000))
        dao.insertSample(VoiceSampleEntity(embedding = ByteArray(8), durationMs = 6_000, capturedAtMs = 2_000))
        assertEquals(2, dao.sampleCount())
        assertEquals(10_000L, dao.totalDurationMs())
        dao.deleteAll()
        assertEquals(0, dao.sampleCount())
        assertEquals(0L, dao.totalDurationMs())
        database.close()
    }
}
