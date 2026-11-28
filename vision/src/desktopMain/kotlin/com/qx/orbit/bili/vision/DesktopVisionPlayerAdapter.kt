package com.qx.orbit.bili.vision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qx.orbit.bili.core.api.CorePlayback
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

internal class DesktopVisionPlayerAdapter : VisionPlayerAdapter, DesktopVisionFrameSource {
    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null
    private var surface: CallbackVideoSurface? = null
    private var playback: CorePlayback? = null
    private var listener: VisionPlayerListener? = null
    private var mediaIndex = 0
    private var candidateIndex = 0
    private var pendingPosition = 0L
    private var requestedSpeed = 1f
    private var wantsPlayback = false
    private var released = false
    private var ended = false
    @Volatile private var activeMedia = 0
    private val framePending = AtomicBoolean()
    private var pixels = ByteArray(0)
    override var frame by mutableStateOf<Image?>(null)
        private set

    override val currentPosition: Long get() = player?.status()?.time()?.takeIf { it >= 0 } ?: pendingPosition
    override val duration: Long get() = player?.status()?.length()?.coerceAtLeast(0) ?: 0
    override val currentMediaItemIndex: Int get() = mediaIndex
    override val mediaItemCount: Int get() = playback?.let { if (it.audioUrl != null) 1 else it.urls.size } ?: 0
    override val segmentDurationsMs: List<Long> get() = playback?.segmentDurationsMs.orEmpty()
    override val isPlaying: Boolean get() = player?.status()?.isPlaying == true

    override fun setListener(listener: VisionPlayerListener?) {
        this.listener = listener
    }

    override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) {
        require(playback.urls.isNotEmpty())
        this.playback = playback
        mediaIndex = segment.coerceIn(0, mediaItemCount - 1)
        pendingPosition = positionMs.coerceAtLeast(0)
        candidateIndex = 0
        ended = false
        stopMedia()
        if (wantsPlayback) startMedia()
    }

    override fun play() {
        if (released) return
        wantsPlayback = true
        if (ended) {
            mediaIndex = 0
            candidateIndex = 0
            pendingPosition = 0L
            ended = false
            stopMedia()
        }
        val current = player
        if (current == null) startMedia()
        else current.controls().setPause(false)
    }

    override fun pause() {
        wantsPlayback = false
        player?.controls()?.setPause(true)
    }

    override fun seekTo(positionMs: Long) {
        pendingPosition = positionMs.coerceAtLeast(0)
        player?.controls()?.setTime(pendingPosition)
    }

    override fun setPlaybackSpeed(speed: Float) {
        requestedSpeed = supportedPlaybackSpeed(speed)
        player?.controls()?.setRate(requestedSpeed)
    }

    private fun startMedia() {
        val current = playback ?: return
        val groups = playbackAddressGroups(current)
        val addresses = groups[mediaIndex]
        val audios = if (current.audioUrl != null) groups[1] else emptyList()
        val audioCount = audios.size.coerceAtLeast(1)
        val address = addresses.getOrNull(candidateIndex / audioCount)
        if (address == null) {
            reportError("VLC 播放失败：当前分段的地址已全部尝试")
            return
        }

        try {
            val mediaFactory = factory ?: MediaPlayerFactory("--no-video-title-show", "--quiet", "--avcodec-hw=any").also { factory = it }
            val token = ++activeMedia
            val mediaPlayer = mediaFactory.mediaPlayers().newEmbeddedMediaPlayer()
            player = mediaPlayer
            val format = object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat =
                    RV32BufferFormat(sourceWidth, sourceHeight)

                override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit
                override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
            }
            val render = object : RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer) = Unit
                override fun unlock(mediaPlayer: MediaPlayer) = Unit

                override fun display(
                    mediaPlayer: MediaPlayer,
                    buffers: Array<ByteBuffer>,
                    layout: BufferFormat,
                    width: Int,
                    height: Int,
                ) {
                    publishFrame(token, buffers[0], layout.pitches[0], layout.width, layout.height)
                }
            }
            val videoSurface = mediaFactory.videoSurfaces().newVideoSurface(format, render, true)
            surface = videoSurface
            mediaPlayer.videoSurface().set(videoSurface)
            mediaPlayer.events().addMediaPlayerEventListener(mediaEvents(token))
            val options = mutableListOf(
                ":http-referrer=https://www.bilibili.com/",
                ":http-user-agent=Mozilla/5.0 Chrome/122 Safari/537.36",
            )
            audios.getOrNull(candidateIndex % audioCount)?.let { options.add(":input-slave=$it") }
            listener?.onPlaybackStateChanged(true, false)
            if (!mediaPlayer.media().play(address, *options.toTypedArray())) failOver()
        } catch (_: LinkageError) {
            stopMedia()
            reportError("未找到兼容的 LibVLC 3，请安装与系统架构匹配的 VLC。")
        } catch (_: RuntimeException) {
            stopMedia()
            reportError("无法初始化 VLC 播放器，请检查 LibVLC 3 安装。")
        }
    }

    private fun mediaEvents(token: Int) = object : MediaPlayerEventAdapter() {
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = onUi(token) {
            listener?.onPlaybackStateChanged(newCache < 100f, false)
        }

        override fun playing(mediaPlayer: MediaPlayer) = onUi(token) {
            mediaPlayer.controls().setRate(requestedSpeed)
            if (pendingPosition > 0 && mediaPlayer.status().isSeekable) {
                mediaPlayer.controls().setTime(pendingPosition)
                pendingPosition = 0
            }
            if (!wantsPlayback) mediaPlayer.controls().setPause(true)
            listener?.onPlaybackStateChanged(false, false)
            listener?.onIsPlayingChanged(wantsPlayback)
        }

        override fun paused(mediaPlayer: MediaPlayer) = onUi(token) {
            listener?.onIsPlayingChanged(false)
        }

        override fun seekableChanged(mediaPlayer: MediaPlayer, newSeekable: Int) = onUi(token) {
            if (newSeekable != 0 && pendingPosition > 0) {
                mediaPlayer.controls().setTime(pendingPosition)
                pendingPosition = 0
            }
        }

        override fun finished(mediaPlayer: MediaPlayer) = onUi(token) {
            if (mediaIndex + 1 < mediaItemCount) {
                stopMedia()
                mediaIndex++
                candidateIndex = 0
                pendingPosition = 0
                if (wantsPlayback) startMedia()
            } else {
                ended = true
                listener?.onIsPlayingChanged(false)
                listener?.onPlaybackStateChanged(false, true)
            }
        }

        override fun error(mediaPlayer: MediaPlayer) = onUi(token) { failOver() }
    }

    private fun onUi(token: Int, action: () -> Unit) {
        SwingUtilities.invokeLater { if (!released && token == activeMedia) action() }
    }

    private fun publishFrame(token: Int, source: ByteBuffer, pitch: Int, width: Int, height: Int) {
        if (token != activeMedia || width <= 0 || height <= 0 || !framePending.compareAndSet(false, true)) return
        val length = pitch * height
        val buffer = source.duplicate()
        buffer.clear()
        if (length <= 0 || length > buffer.remaining()) {
            framePending.set(false)
            return
        }
        if (pixels.size != length) pixels = ByteArray(length)
        buffer.get(pixels)
        SwingUtilities.invokeLater {
            try {
                if (!released && token == activeMedia) {
                    val previous = frame
                    frame = Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, pitch)
                    if (previous?.width != width || previous?.height != height) {
                        listener?.onVideoSizeChanged(width.toFloat() / height)
                    }
                    previous?.close()
                }
            } finally {
                framePending.set(false)
            }
        }
    }

    private fun failOver() {
        pendingPosition = currentPosition
        candidateIndex++
        stopMedia()
        if (wantsPlayback) startMedia()
    }

    private fun reportError(message: String) {
        wantsPlayback = false
        listener?.onIsPlayingChanged(false)
        listener?.onPlaybackStateChanged(false, false)
        listener?.onError(message)
    }

    private fun stopMedia() {
        activeMedia++
        val current = player
        val activeSurface = surface
        player = null
        current?.controls()?.stop()
        current?.release()
        java.lang.ref.Reference.reachabilityFence(activeSurface)
        surface = null
        val previous = frame
        frame = null
        previous?.close()
    }

    override fun release() {
        if (released) return
        released = true
        stopMedia()
        factory?.release()
        factory = null
        listener = null
    }
}
