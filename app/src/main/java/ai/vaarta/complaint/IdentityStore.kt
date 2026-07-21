package ai.vaarta.complaint

import ai.vaarta.core.data.db.IdentityDao
import ai.vaarta.core.data.db.IdentityEntity
import ai.vaarta.core.data.db.VaartaDatabase
import android.content.Context

data class IdentityDetails(
    val name: String,
    val address: String,
    val mobile: String,
    val email: String,
    val idType: String,
)

/** Thin suspend-fun facade over [IdentityDao] so callers never touch Room types (mirrors GuardianStore). */
class IdentityStore private constructor(private val dao: IdentityDao) {
    suspend fun get(): IdentityDetails? = dao.get()?.let {
        IdentityDetails(it.name, it.address, it.mobile, it.email, it.idType)
    }
    suspend fun set(d: IdentityDetails) =
        dao.set(IdentityEntity(name = d.name, address = d.address, mobile = d.mobile, email = d.email, idType = d.idType))
    suspend fun clear() = dao.clear()

    companion object {
        fun create(context: Context): IdentityStore =
            IdentityStore(VaartaDatabase.get(context).identityDao())
    }
}
