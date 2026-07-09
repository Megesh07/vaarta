package ai.vaarta.core.data.db

import ai.vaarta.core.data.crypto.DatabaseKeyManager
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The encrypted history database (Phase 4B, ADR-0004). Room over SQLCipher; the passphrase is minted
 * and wrapped by [DatabaseKeyManager] (Keystore-backed) — it is never in source or shipped assets.
 */
@Database(
    entities = [CallSessionEntity::class, TurnEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VaartaDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val DB_NAME = "vaarta-history.db"

        @Volatile private var instance: VaartaDatabase? = null

        fun get(context: Context): VaartaDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(appContext: Context): VaartaDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager(appContext).getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(appContext, VaartaDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
