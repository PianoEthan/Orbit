package com.qx.orbit.bili.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ScreenMode(val value: Int) {
    AUTO(0), ROUND(1), SQUARE(2)
}

object AppConfig {
    private const val PREFS_NAME = "orbit_config"
    private const val KEY_SCREEN_MODE = "screen_mode"
    private const val KEY_DPI_SCALE = "dpi_scale"

    var screenMode by mutableStateOf(ScreenMode.AUTO)
    var dpiScale by mutableFloatStateOf(1.0f)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_SCREEN_MODE, ScreenMode.AUTO.value)
        screenMode = ScreenMode.entries.find { it.value == saved } ?: ScreenMode.AUTO
        dpiScale = prefs.getFloat(KEY_DPI_SCALE, 1.0f)
    }

    fun saveScreenMode(context: Context, mode: ScreenMode) {
        screenMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_SCREEN_MODE, mode.value)
            apply()
        }
    }

    fun saveDpiScale(context: Context, scale: Float) {
        dpiScale = scale
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_DPI_SCALE, scale)
            apply()
        }
    }
}
