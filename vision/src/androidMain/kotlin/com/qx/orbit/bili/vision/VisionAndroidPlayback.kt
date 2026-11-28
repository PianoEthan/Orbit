package com.qx.orbit.bili.vision

import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.qx.orbit.bili.core.api.CorePlayback
import io.github.ynotbili.dfmnext.ui.widget.DanmakuView

internal actual val platformVisionEngines = listOf(VisionPlayerEngine.IJK, VisionPlayerEngine.MEDIA3)

internal actual fun platformVisionEngine(engine: VisionPlayerEngine): VisionPlayerEngine =
    effectiveVisionEngine(engine.takeIf { it in platformVisionEngines } ?: VisionPlayerEngine.IJK, Build.SUPPORTED_ABIS.toList())

@Composable
internal actual fun visionPlayerFactory(): (VisionPlayerEngine) -> VisionPlayerAdapter {
    val context = LocalContext.current
    return remember(context) { { engine -> createVisionPlayerAdapter(context, engine) } }
}

@Composable
internal actual fun VisionPlayerSurface(player: VisionPlayerAdapter, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view -> (player as? AndroidVisionPlayerAdapter)?.bindSurfaceView(view) }
        },
        modifier = modifier,
    )
}

@Composable
internal actual fun VisionDanmakuLayer(host: VisionDanmakuHost, modifier: Modifier) {
    AndroidView(factory = { context -> DanmakuView(context).also(host::bindView) }, modifier = modifier)
}

internal actual fun visionPlaybackKey(event: KeyEvent): PlaybackKey =
    when (event.nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> PlaybackKey.CONFIRM
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> PlaybackKey.LEFT
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> PlaybackKey.RIGHT
        AndroidKeyEvent.KEYCODE_DPAD_UP -> PlaybackKey.UP
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> PlaybackKey.DOWN
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_HEADSETHOOK, AndroidKeyEvent.KEYCODE_SPACE -> PlaybackKey.PLAY_PAUSE
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> PlaybackKey.PLAY
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackKey.PAUSE
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlaybackKey.FAST_FORWARD
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> PlaybackKey.REWIND
        else -> PlaybackKey.OTHER
    }

internal actual fun visionPlaybackDescription(engine: VisionPlayerEngine, playback: CorePlayback?): String {
    val current = when {
        playback?.audioUrl != null -> "当前使用 DASH · ExoPlayer 音视频合并"
        engine == VisionPlayerEngine.IJK -> "当前使用 MP4 · ijkPlayer"
        else -> "当前使用 MP4 · ExoPlayer"
    }
    return "$current；高清仅有 DASH 时自动切换内核，实际画质以服务器授权为准。"
}
