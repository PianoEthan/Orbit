package com.qx.orbit.bili.vision

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VisionThemeMode(val title: String) {
    DARK("深色模式 (默认)"), LIGHT("浅色模式"), SYSTEM("跟随系统")
}

object VisionPreferences {
    private const val PREFS_NAME = "orbit_vision_prefs"
    var themeMode by mutableStateOf(VisionThemeMode.DARK)
        private set
    var dynamicColor by mutableStateOf(false)
        private set
    var controlsTimeoutMs by mutableStateOf(4500L)
        private set
    var preferNineKey by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = runCatching { VisionThemeMode.valueOf(prefs.getString("theme_mode", VisionThemeMode.DARK.name).orEmpty()) }.getOrDefault(VisionThemeMode.DARK)
        dynamicColor = prefs.getBoolean("dynamic_color", false)
        controlsTimeoutMs = prefs.getLong("controls_timeout", 4500L).takeIf { it in listOf(0L, 4500L, 8000L) } ?: 4500L
        preferNineKey = prefs.getBoolean("nine_key", false)
    }

    fun setThemeMode(context: Context, mode: VisionThemeMode) {
        themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("theme_mode", mode.name).apply()
    }
    fun setDynamicColor(context: Context, enabled: Boolean) {
        dynamicColor = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("dynamic_color", enabled).apply()
    }
    fun setControlsTimeout(context: Context, timeout: Long) {
        require(timeout in listOf(0L, 4500L, 8000L))
        controlsTimeoutMs = timeout
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong("controls_timeout", timeout).apply()
    }
    fun setPreferNineKey(context: Context, enabled: Boolean) {
        preferNineKey = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("nine_key", enabled).apply()
    }
}
