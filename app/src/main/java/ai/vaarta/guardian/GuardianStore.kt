package ai.vaarta.guardian

import android.content.SharedPreferences

data class Guardian(val name: String, val number: String)

/** Stores the one chosen guardian contact locally (spec §7). Mirrors AwarenessStore's
 *  SharedPreferences use — no new persistence layer, nothing leaves the device. */
class GuardianStore(private val prefs: SharedPreferences) {
    fun get(): Guardian? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val number = prefs.getString(KEY_NUMBER, null) ?: return null
        return Guardian(name, number)
    }
    fun set(name: String, number: String) =
        prefs.edit().putString(KEY_NAME, name).putString(KEY_NUMBER, number).apply()
    fun clear() = prefs.edit().remove(KEY_NAME).remove(KEY_NUMBER).apply()

    companion object {
        /** Shared preferences file name — the same instance both HelpScreen and MainActivity
         *  read/write through, matching AwarenessStore's single-file convention. */
        const val PREFS_NAME = "vaarta_guardian_prefs"
        private const val KEY_NAME = "guardian_name"
        private const val KEY_NUMBER = "guardian_number"
    }
}
