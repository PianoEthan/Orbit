package com.qx.orbit.bili.vision

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

fun initializeVisionAndroid(context: Context) {
    val appContext = context.applicationContext
    initializeVisionStores { name ->
        AndroidVisionStore(appContext.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}

@Composable
internal actual fun visionIsTv(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

internal actual val visionIsDesktop: Boolean = false

@Composable
internal actual fun visionWindowWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Composable
internal actual fun visionDynamicColorsAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal actual fun visionDynamicColors(dark: Boolean): ColorScheme? {
    if (!visionDynamicColorsAvailable()) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

internal actual fun visionIsRepeatedKey(event: KeyEvent): Boolean = event.nativeKeyEvent.repeatCount > 0

internal actual fun visionElapsedTimeMs(): Long = SystemClock.elapsedRealtime()

private class AndroidVisionStore(private val preferences: SharedPreferences) : VisionStore {
    override fun getString(key: String, default: String): String = preferences.getString(key, default) ?: default
    override fun getLong(key: String, default: Long): Long = preferences.getLong(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)
    override fun getFloat(key: String, default: Float): Float = preferences.getFloat(key, default)

    override fun put(vararg values: Pair<String, Any>) {
        val editor = preferences.edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                else -> error("Unsupported preference type for $key")
            }
        }
        editor.apply()
    }

    override fun remove(key: String) { preferences.edit().remove(key).apply() }
    override fun clear() { preferences.edit().clear().apply() }
}
