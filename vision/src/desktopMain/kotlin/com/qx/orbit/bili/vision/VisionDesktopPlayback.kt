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
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import uk.co.caprica.vlcj.binding.lib.LibVlc
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

internal interface DesktopVisionFrameSource {
    val frame: Image?
}

internal actual val platformVisionEngines = listOf(VisionPlayerEngine.MPV, VisionPlayerEngine.VLC)

private val vlcAvailable by lazy {
    try {
        NativeDiscovery().discover() && LibVlc.libvlc_get_version().startsWith("3.")
    } catch (_: LinkageError) {
        false
    } catch (_: RuntimeException) {
        false
    }
}

internal actual fun isVisionEngineAvailable(engine: VisionPlayerEngine): Boolean = when (engine) {
    VisionPlayerEngine.MPV -> true
    VisionPlayerEngine.VLC -> vlcAvailable
    else -> false
}

internal actual fun platformVisionEngine(engine: VisionPlayerEngine): VisionPlayerEngine =
    engine.takeIf(::isVisionEngineAvailable) ?: VisionPlayerEngine.MPV

@Composable
internal actual fun visionPlayerFactory(): (VisionPlayerEngine) -> VisionPlayerAdapter =
    remember {
        { engine ->
            when (platformVisionEngine(engine)) {
                VisionPlayerEngine.MPV -> DesktopMpvPlayerAdapter()
                else -> DesktopVisionPlayerAdapter()
            }
        }
    }

@Composable
internal actual fun VisionPlayerSurface(player: VisionPlayerAdapter, modifier: Modifier) {
    val desktopPlayer = player as? DesktopVisionFrameSource ?: return
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
    if (playback?.audioUrl != null) "当前使用 DASH · ${engine.title} 音视频合并" else "当前使用 ${engine.title} 播放"
