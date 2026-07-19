package ai.vaarta.guardian

import ai.vaarta.core.data.db.GuardianDao
import ai.vaarta.core.data.db.GuardianEntity
import ai.vaarta.core.data.db.VaartaDatabase
import android.content.Context

data class Guardian(val name: String, val number: String)

/**
 * Stores the one chosen guardian contact in the encrypted history database (Task 9 hardening fix —
 * this used to be a plain SharedPreferences store, which regressed against
 * `docs/PRIVACY_SECURITY.md`'s data-inventory line requiring SQLCipher for guardian contacts).
 * Mirrors `HistoryRepository`'s DAO-wrapping layering: a thin `suspend`-fun facade over [GuardianDao]
 * so callers never touch Room types directly.
 */
class GuardianStore private constructor(private val dao: GuardianDao) {
    suspend fun get(): Guardian? = dao.get()?.let { Guardian(it.name, it.number) }

    suspend fun set(name: String, number: String) = dao.set(GuardianEntity(name = name, number = number))

    suspend fun clear() = dao.clear()

    companion object {
        fun create(context: Context): GuardianStore = GuardianStore(VaartaDatabase.get(context).guardianDao())
    }
}
