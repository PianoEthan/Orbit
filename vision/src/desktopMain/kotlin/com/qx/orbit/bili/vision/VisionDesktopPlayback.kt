package com.qx.orbit.bili.vision

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import com.qx.orbit.bili.core.api.CorePlayback
import org.jetbrains.skia.Rect

internal actual val platformVisionEngines = listOf(VisionPlayerEngine.VLC)

internal actual fun platformVisionEngine(engine: VisionPlayerEngine): VisionPlayerEngine = VisionPlayerEngine.VLC

@Composable
internal actual fun visionPlayerFactory(): (VisionPlayerEngine) -> VisionPlayerAdapter =
    remember { { DesktopVisionPlayerAdapter() } }

@Composable
internal actual fun VisionPlayerSurface(player: VisionPlayerAdapter, modifier: Modifier) {
    val desktopPlayer = player as? DesktopVisionPlayerAdapter ?: return
    Canvas(modifier) {
        val frame = desktopPlayer.frame ?: return@Canvas
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawImageRect(frame, Rect.makeWH(size.width, size.height))
        }
    }
}

internal actual fun visionPlaybackKey(event: KeyEvent): PlaybackKey = when (event.key) {
    Key.Enter, Key.NumPadEnter -> PlaybackKey.CONFIRM
    Key.DirectionLeft -> PlaybackKey.LEFT
    Key.DirectionRight -> PlaybackKey.RIGHT
    Key.DirectionUp -> PlaybackKey.UP
    Key.DirectionDown -> PlaybackKey.DOWN
    Key.Spacebar -> PlaybackKey.PLAY_PAUSE
    else -> PlaybackKey.OTHER
}

internal actual fun visionPlaybackDescription(engine: VisionPlayerEngine, playback: CorePlayback?): String =
    if (playback?.audioUrl != null) "当前使用 DASH · VLC 音视频合并" else "当前使用 VLC 播放"
