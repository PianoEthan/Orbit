package com.qx.orbit.bili.vision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VisionThemeMode(val title: String) {
    DARK("深色模式 (默认)"), LIGHT("浅色模式"), SYSTEM("跟随系统")
}

enum class VisionPlayerEngine(val title: String) {
    IJK("ijkPlayer（优先硬件解码）"),
    MEDIA3("ExoPlayer / Media3 (原生内核)"),
    VLC("VLC"),
    MPV("mpv")
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
    var loopPlayback by mutableStateOf(false)
        private set
    var autoNextPart by mutableStateOf(false)
        private set
    var videoZoomEnabled by mutableStateOf(true)
        private set

    private var store: VisionStore? = null

    internal fun init(openStore: (String) -> VisionStore) {
        val prefs = openStore(PREFS_NAME)
        store = prefs
        themeMode = runCatching { VisionThemeMode.valueOf(prefs.getString("theme_mode", VisionThemeMode.DARK.name).orEmpty()) }.getOrDefault(VisionThemeMode.DARK)
        dynamicColor = prefs.getBoolean("dynamic_color", false)
        controlsTimeoutMs = prefs.getLong("controls_timeout", 4500L).takeIf { it in listOf(0L, 4500L, 8000L) } ?: 4500L
        loopPlayback = prefs.getBoolean("loop_playback", false)
        autoNextPart = prefs.getBoolean("auto_next_part", false)
        videoZoomEnabled = prefs.getBoolean("video_zoom", true)
        preferNineKey = prefs.getBoolean("nine_key", false)
        playerEngine = runCatching { VisionPlayerEngine.valueOf(prefs.getString("player_engine", VisionPlayerEngine.IJK.name).orEmpty()) }.getOrDefault(VisionPlayerEngine.IJK)
        danmakuEnabled = prefs.getBoolean("danmaku_enabled", true)
        danmakuAlpha = prefs.getFloat("danmaku_alpha", 0.85f).coerceIn(0.2f, 1f)
        danmakuTextScale = prefs.getFloat("danmaku_text_scale", 1.0f).coerceIn(0.5f, 2.0f)
    }

    fun updateThemeMode(mode: VisionThemeMode) {
        themeMode = mode
        store?.put("theme_mode" to mode.name)
    }
    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        store?.put("dynamic_color" to enabled)
    }
    fun updateControlsTimeout(timeout: Long) {
        require(timeout in listOf(0L, 4500L, 8000L))
        controlsTimeoutMs = timeout
        store?.put("controls_timeout" to timeout)
    }
    fun updatePreferNineKey(enabled: Boolean) {
        preferNineKey = enabled
        store?.put("nine_key" to enabled)
    }
    fun updatePlayerEngine(engine: VisionPlayerEngine) {
        playerEngine = engine
        store?.put("player_engine" to engine.name)
    }
    fun updateDanmakuEnabled(enabled: Boolean) {
        danmakuEnabled = enabled
        store?.put("danmaku_enabled" to enabled)
    }
    fun updateDanmakuAlpha(alpha: Float) {
        val bounded = alpha.coerceIn(0.2f, 1f)
        danmakuAlpha = bounded
        store?.put("danmaku_alpha" to bounded)
    }
    fun updateLoopPlayback(enabled: Boolean) {
        loopPlayback = enabled
        store?.put("loop_playback" to enabled)
    }
    fun updateAutoNextPart(enabled: Boolean) {
        autoNextPart = enabled
        store?.put("auto_next_part" to enabled)
    }
    fun updateVideoZoomEnabled(enabled: Boolean) {
        videoZoomEnabled = enabled
        store?.put("video_zoom" to enabled)
    }
    fun updateDanmakuTextScale(scale: Float) {
        val bounded = scale.coerceIn(0.5f, 2.0f)
        danmakuTextScale = bounded
        store?.put("danmaku_text_scale" to bounded)
    }
}
