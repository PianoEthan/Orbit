package com.qx.orbit.bili.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class LocalPlaybackHistoryStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val data = ConcurrentHashMap<String, Any>()

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { temp[key ?: ""] = value }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = apply { temp[key ?: ""] = values }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { temp[key ?: ""] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { temp[key ?: ""] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { temp[key ?: ""] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { temp[key ?: ""] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { temp[key ?: ""] = null }
            override fun clear(): SharedPreferences.Editor = apply { clear = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) prefs.data.clear()
                for ((k, v) in temp) {
                    if (v == null) prefs.data.remove(k) else prefs.data[k] = v
                }
            }
        }
    }

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        LocalPlaybackHistoryStore.init(fakePrefs)
        LocalPlaybackHistoryStore.clear()
    }

    @Test
    fun saveAndGetProgressByCid() {
        LocalPlaybackHistoryStore.saveProgress(cid = 1001L, localUri = null, progressMs = 45000L, durationMs = 120000L)
        assertEquals(45, LocalPlaybackHistoryStore.getProgressSeconds(1001L, null))
        assertEquals(45000L, LocalPlaybackHistoryStore.getProgressMs(1001L, null))
        assertEquals(120000L, LocalPlaybackHistoryStore.getDurationMs(1001L, null))
    }

    @Test
    fun saveAndGetProgressByUriWhenCidIsZero() {
        val uri = "/storage/emulated/0/Movies/Orbit/test_video.mp4"
        LocalPlaybackHistoryStore.saveProgress(cid = 0L, localUri = uri, progressMs = 30000L, durationMs = 60000L)
        assertEquals(30, LocalPlaybackHistoryStore.getProgressSeconds(0L, uri))
        assertEquals(30000L, LocalPlaybackHistoryStore.getProgressMs(0L, uri))
    }

    @Test
    fun progressResetsToZeroWhenFinished() {
        // When progress is within 3 seconds of the end, it should reset to 0
        LocalPlaybackHistoryStore.saveProgress(cid = 1002L, localUri = null, progressMs = 98500L, durationMs = 100000L)
        assertEquals(0, LocalPlaybackHistoryStore.getProgressSeconds(1002L, null))
        assertEquals(0L, LocalPlaybackHistoryStore.getProgressMs(1002L, null))
    }

    @Test
    fun removeCleansSpecificEntry() {
        LocalPlaybackHistoryStore.saveProgress(cid = 2001L, localUri = null, progressMs = 15000L, durationMs = 60000L)
        assertEquals(15, LocalPlaybackHistoryStore.getProgressSeconds(2001L, null))
        LocalPlaybackHistoryStore.remove(2001L, null)
        assertEquals(0, LocalPlaybackHistoryStore.getProgressSeconds(2001L, null))
    }
}
