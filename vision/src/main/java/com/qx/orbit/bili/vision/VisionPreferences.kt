package com.qx.orbit.bili.vision

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VisionThemeMode(val title: String) {
    DARK("深色模式 (默认)"), LIGHT("浅色模式"), SYSTEM("跟随系统")
}

enum class VisionPlayerEngine(val title: String) {
    IJK("ijkPlayer（优先硬件解码）"),
    MEDIA3("ExoPlayer / Media3 (原生内核)")
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
    var playerEngine by mutableStateOf(VisionPlayerEngine.IJK)
        private set
    var danmakuEnabled by mutableStateOf(true)
        private set
    var danmakuAlpha by mutableFloatStateOf(0.85f)
        private set
    var danmakuTextScale by mutableFloatStateOf(1.0f)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeMode = runCatching { VisionThemeMode.valueOf(prefs.getString("theme_mode", VisionThemeMode.DARK.name).orEmpty()) }.getOrDefault(VisionThemeMode.DARK)
        dynamicColor = prefs.getBoolean("dynamic_color", false)
        controlsTimeoutMs = prefs.getLong("controls_timeout", 4500L).takeIf { it in listOf(0L, 4500L, 8000L) } ?: 4500L
        preferNineKey = prefs.getBoolean("nine_key", false)
        playerEngine = runCatching { VisionPlayerEngine.valueOf(prefs.getString("player_engine", VisionPlayerEngine.IJK.name).orEmpty()) }.getOrDefault(VisionPlayerEngine.IJK)
        danmakuEnabled = prefs.getBoolean("danmaku_enabled", true)
        danmakuAlpha = prefs.getFloat("danmaku_alpha", 0.85f).coerceIn(0.2f, 1f)
        danmakuTextScale = prefs.getFloat("danmaku_text_scale", 1.0f).coerceIn(0.5f, 2.0f)
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
    fun setPlayerEngine(context: Context, engine: VisionPlayerEngine) {
        playerEngine = engine
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("player_engine", engine.name).apply()
    }
    fun setDanmakuEnabled(context: Context, enabled: Boolean) {
        danmakuEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("danmaku_enabled", enabled).apply()
    }
    fun setDanmakuAlpha(context: Context, alpha: Float) {
        val bounded = alpha.coerceIn(0.2f, 1f)
        danmakuAlpha = bounded
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("danmaku_alpha", bounded).apply()
    }
    fun setDanmakuTextScale(context: Context, scale: Float) {
        val bounded = scale.coerceIn(0.5f, 2.0f)
        danmakuTextScale = bounded
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat("danmaku_text_scale", bounded).apply()
    }
}
