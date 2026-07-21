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
class IdentityDaoTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        VaartaDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun sample(name: String = "Asha", mobile: String = "+919000000000") =
        IdentityEntity(name = name, address = "12 MG Road, Pune", mobile = mobile, email = "a@b.in", idType = "PAN")

    @Test fun getReturnsNullWhenEmpty() = runBlocking {
        val d = db(); assertNull(d.identityDao().get()); d.close()
    }

    @Test fun setThenGetRoundTrips() = runBlocking {
        val d = db(); d.identityDao().set(sample())
        val s = d.identityDao().get()
        assertEquals("Asha", s?.name); assertEquals("PAN", s?.idType); d.close()
    }

    @Test fun setCalledTwiceReplacesNotDuplicates() = runBlocking {
        val d = db(); d.identityDao().set(sample())
        d.identityDao().set(sample(name = "Ravi", mobile = "+919111111111"))
        val s = d.identityDao().get(); assertEquals("Ravi", s?.name); d.close()
    }

    @Test fun clearEmptiesTheTable() = runBlocking {
        val d = db(); d.identityDao().set(sample()); d.identityDao().clear()
        assertNull(d.identityDao().get()); d.close()
    }
}
