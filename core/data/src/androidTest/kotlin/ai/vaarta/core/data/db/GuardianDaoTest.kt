package ai.vaarta.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuardianDaoTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        VaartaDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Test
    fun getReturnsNullWhenEmpty() = runBlocking {
        val database = db()
        val dao = database.guardianDao()
        assertNull(dao.get())
        database.close()
    }

    @Test
    fun setThenGetRoundTrips() = runBlocking {
        val database = db()
        val dao = database.guardianDao()
        dao.set(GuardianEntity(name = "Amma", number = "+919000000000"))
        val stored = dao.get()
        assertEquals("Amma", stored?.name)
        assertEquals("+919000000000", stored?.number)
        database.close()
    }

    @Test
    fun setCalledTwiceReplacesNotDuplicates() = runBlocking {
        val database = db()
        val dao = database.guardianDao()
        dao.set(GuardianEntity(name = "Amma", number = "+919000000000"))
        dao.set(GuardianEntity(name = "Papa", number = "+919111111111"))
        val stored = dao.get()
        assertEquals("Papa", stored?.name)
        assertEquals("+919111111111", stored?.number)
        database.close()
    }

    @Test
    fun clearEmptiesTheTable() = runBlocking {
        val database = db()
        val dao = database.guardianDao()
        dao.set(GuardianEntity(name = "Amma", number = "+919000000000"))
        dao.clear()
        assertNull(dao.get())
        database.close()
    }
}
