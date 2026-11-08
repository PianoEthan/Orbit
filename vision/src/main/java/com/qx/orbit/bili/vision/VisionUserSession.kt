package com.qx.orbit.bili.vision

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qx.orbit.bili.core.api.BilibiliDeviceFingerprint
import com.qx.orbit.bili.core.api.CoreUserProfile
import com.qx.orbit.bili.core.api.mergeCookieString
import com.qx.orbit.bili.core.api.parseCookieValue

object VisionUserSession {
    private const val PREFS_NAME = "orbit_vision_user"
    private const val KEY_COOKIE = "user_cookie"
    private const val KEY_MID = "user_mid"
    private var preferences: SharedPreferences? = null

    var cookie by mutableStateOf("")
        private set
    var mid by mutableStateOf(0L)
        private set
    var profile by mutableStateOf<CoreUserProfile?>(null)
        private set
    var sessionExpired by mutableStateOf(false)
        private set

    val isLoggedIn: Boolean get() = validVisionSession(cookie, mid)
    val csrf: String get() = parseCookieValue(cookie, "bili_jct")

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        val storedCookie = prefs.getString(KEY_COOKIE, "").orEmpty()
        val storedMid = prefs.getLong(KEY_MID, 0L)
        if (validVisionSession(storedCookie, storedMid)) {
            cookie = storedCookie
            mid = storedMid
        } else {
            clear(expired = storedCookie.isNotBlank() || storedMid > 0)
        }
        if (profile?.mid != mid) profile = null
    }

    fun onLoginSuccess(context: Context, newCookie: String, newMid: Long) {
        require(validVisionSession(newCookie, newMid))
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        profile = null
        sessionExpired = false
        val existingBuvids = mutableMapOf<String, String>()
        val oldB3 = parseCookieValue(cookie, "buvid3")
        val oldB4 = parseCookieValue(cookie, "buvid4")
        if (oldB3.isNotBlank()) existingBuvids["buvid3"] = oldB3
        if (oldB4.isNotBlank()) existingBuvids["buvid4"] = oldB4
        val mergedWithExisting = if (existingBuvids.isNotEmpty()) mergeCookieString(newCookie, existingBuvids) else newCookie
        val finalCookie = BilibiliDeviceFingerprint.attachIfMissing(mergedWithExisting)
        cookie = finalCookie
        mid = newMid
        prefs.edit().putString(KEY_COOKIE, finalCookie).putLong(KEY_MID, newMid).apply()
    }

    fun updateCookie(updatedCookie: String) {
        if (updatedCookie == cookie || updatedCookie.isBlank()) return
        val currentMid = mid
        if (currentMid > 0 && !validVisionSession(updatedCookie, currentMid)) return
        cookie = updatedCookie
        preferences?.edit()?.putString(KEY_COOKIE, updatedCookie)?.apply()
    }

    fun updateProfile(newProfile: CoreUserProfile) {
        when (visionProfileDisposition(cookie, mid, newProfile)) {
            VisionProfileDisposition.ACCEPT -> profile = newProfile
            VisionProfileDisposition.EXPIRED -> clear(expired = true)
            VisionProfileDisposition.IGNORE -> Unit
        }
    }

    fun logout(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        clear(expired = false)
    }

    private fun clear(expired: Boolean) {
        cookie = ""
        mid = 0L
        profile = null
        sessionExpired = expired
        preferences?.edit()?.clear()?.apply()
    }
}
