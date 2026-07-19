package ai.vaarta.guardian

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Plain-JUnit unit test (no Robolectric, matching the rest of app/src/test — see IndiaContextTest):
 * [FakePrefs] is a minimal in-memory [SharedPreferences] double, so GuardianStore's only Android
 * dependency (the SharedPreferences interface) stays trivially fakeable off-device.
 */
class GuardianStoreTest {
    @Test
    fun `set then get round-trips, clear removes`() {
        val store = GuardianStore(FakePrefs())
        assertNull(store.get())
        store.set("Amma", "+919000000000")
        assertEquals(Guardian("Amma", "+919000000000"), store.get())
        store.clear()
        assertNull(store.get())
    }
}

/** Minimal in-memory [SharedPreferences] fake — only the subset GuardianStore actually calls. */
private class FakePrefs : SharedPreferences {
    val values = mutableMapOf<String, String?>()

    override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun getAll(): MutableMap<String, *> = values
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

/** Applies edits synchronously to the backing [FakePrefs] map — no async commit semantics needed. */
private class FakeEditor(private val prefs: FakePrefs) : SharedPreferences.Editor {
    private val puts = mutableMapOf<String, String?>()
    private val removals = mutableSetOf<String>()
    private var clearAll = false

    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        if (key != null) puts[key] = value
        return this
    }
    override fun remove(key: String?): SharedPreferences.Editor {
        if (key != null) removals.add(key)
        return this
    }
    override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
    override fun apply() { commit() }
    override fun commit(): Boolean {
        if (clearAll) prefs.values.clear()
        removals.forEach { prefs.values.remove(it) }
        puts.forEach { (k, v) -> prefs.values[k] = v }
        return true
    }

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
}
