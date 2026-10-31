package dev.alder

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStoreTest {
    @Test
    fun legacyAuthKeyAndSharingKeysMigrateWithoutLosingTheAccount() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("authKey", "account-key").putString("streamSource:2", "2").apply()

        val profile = SharedPreferencesProfileStore(prefs).all().single()

        assertEquals("1", profile.id)
        assertEquals(AccountRef(Accounts.STREMIO, "account-key"), profile.account)
        assertEquals(listOf(SourceRef(Accounts.STREMIO, "account-key")), profile.sources)
        assertTrue(prefs.getBoolean("shareStreams:2", false))
        assertFalse(prefs.contains("authKey"))
        assertFalse(prefs.contains("streamSource:2"))
    }

    @Test
    fun oldProfileJsonAndNewSourceListsSurviveStoreRoundTrips() {
        val prefs = MemoryPreferences()
        val old = JSONObject().put("id", "2").put("name", "Me").put("color", Profiles.COLORS[0])
            .put("authKey", "old-key").put("pin", "1234")
        prefs.edit().putString("profiles", JSONArray().put(old).toString()).apply()
        val store = SharedPreferencesProfileStore(prefs)
        val prior = store.all().single()
        assertEquals(AccountRef(Accounts.STREMIO, "old-key"), prior.account)
        assertEquals("1234", prior.pin)

        val sources = listOf(SourceRef("catalog", "catalog-key"), SourceRef(Accounts.STREMIO, "source-key"))
        store.save(listOf(Profile(prior.id, prior.name, prior.color, prior.account, prior.pin, sources)))
        val restored = store.all().single()

        assertEquals(prior.account, restored.account)
        assertEquals(prior.pin, restored.pin)
        assertEquals(sources, restored.sources)
    }
}

private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun edit(): SharedPreferences.Editor = MemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String, value: String?) = change(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = change(key, values?.toSet())
        override fun putInt(key: String, value: Int) = change(key, value)
        override fun putLong(key: String, value: Long) = change(key, value)
        override fun putFloat(key: String, value: Float) = change(key, value)
        override fun putBoolean(key: String, value: Boolean) = change(key, value)
        override fun remove(key: String) = change(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clearFirst = true; changes.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearFirst) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }

        private fun change(key: String, value: Any?) = apply { changes[key] = value }
    }
}
