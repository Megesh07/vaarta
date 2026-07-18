package ai.vaarta.core.data.db

import ai.vaarta.core.data.crypto.DatabaseKeyManager
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The encrypted history database (Phase 4B, ADR-0004). Room over SQLCipher; the passphrase is minted
 * and wrapped by [DatabaseKeyManager] (Keystore-backed) — it is never in source or shipped assets.
 */
@Database(
    entities = [CallSessionEntity::class, TurnEntity::class, VoiceSampleEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VaartaDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun voiceprintDao(): VoiceprintDao

    companion object {
        private const val DB_NAME = "vaarta-history.db"

        /** v1 → v2 (VAARTA v2 unified Conversations): add the nullable `title` column. Additive and
         *  guarded — existing saved calls survive the upgrade (their title stays null → derived at
         *  render). The new SessionSource.CHAT / TurnKind.ASSISTANT values need no schema change
         *  (Converters stores enums as strings and reads them tolerantly). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_session ADD COLUMN title TEXT")
            }
        }

        /** v2 -> v3 (Part D, 2026-07-18): the voice_sample table for zero-enrollment speaker
         *  attribution. New table only — no existing data touched. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_sample` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `embedding` BLOB NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `captured_at_ms` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
