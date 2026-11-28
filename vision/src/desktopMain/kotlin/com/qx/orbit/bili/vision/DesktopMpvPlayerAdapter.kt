package com.qx.orbit.bili.vision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qx.orbit.bili.core.api.CorePlayback
import org.jetbrains.skia.Image
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

internal class DesktopMpvPlayerAdapter : VisionPlayerAdapter, DesktopVisionFrameSource {
    private var playback: CorePlayback? = null
    private var session: MpvPlaybackSession? = null
    private var listener: VisionPlayerListener? = null
    private var mediaIndex = 0
    private var candidate = 0
    private var position = 0L
    private var mediaDuration = 0L
    private var speed = 1f
    private var wantsPlayback = false
    private var playing = false
    private var buffering = false
    private var ended = false
    private var released = false
    private var generation = 0
    private val framePending = AtomicBoolean()

    override var frame by mutableStateOf<Image?>(null)
        private set
    override val currentPosition: Long get() = position
    override val duration: Long get() = mediaDuration
    override val currentMediaItemIndex: Int get() = mediaIndex
    override val mediaItemCount: Int get() = playback?.let { if (it.audioUrl == null) it.urls.size else 1 } ?: 0
    override val segmentDurationsMs: List<Long> get() = playback?.segmentDurationsMs.orEmpty()
    override val isPlaying: Boolean get() = playing

    override fun setListener(listener: VisionPlayerListener?) { this.listener = listener }

    override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) {
        require(playback.urls.isNotEmpty())
        stopSession()
        this.playback = playback
        mediaIndex = segment.coerceIn(0, mediaItemCount - 1)
        candidate = 0
        position = positionMs.coerceAtLeast(0)
        mediaDuration = 0
        ended = false
        if (wantsPlayback) startSession()
    }

    override fun play() {
        if (released) return
        wantsPlayback = true
        if (ended) {
            mediaIndex = 0
            candidate = 0
            position = 0
            mediaDuration = 0
            ended = false
        }
        val current = session
        if (current == null) startSession() else current.setPaused(false)
    }

    override fun pause() {
        wantsPlayback = false
        session?.setPaused(true)
        updatePlaying(false)
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs.coerceAtLeast(0)
        session?.seek(position)
    }

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = supportedPlaybackSpeed(speed)
        session?.setSpeed(this.speed)
    }

    private fun startSession() {
        val source = playback ?: return
        val groups = playbackAddressGroups(source)
        val audio = if (source.audioUrl != null) groups[1] else emptyList()
        val audioCount = audio.size.coerceAtLeast(1)
        val address = groups[mediaIndex].getOrNull(candidate / audioCount) ?: return
        val token = ++generation
        updateBuffering(true)
        session = MpvPlaybackSession(
            address = address,
            audioAddress = audio.getOrNull(candidate % audioCount),
            positionMs = position,
            playbackSpeed = speed,
            onUpdate = { status ->
                onUi(token) {
                    position = status.positionMs
                    mediaDuration = status.durationMs
                    updatePlaying(status.playing && wantsPlayback)
                    updateBuffering(status.buffering)
                }
            },
            onFrame = { image -> publishFrame(token, image) },
            onEnd = { message, retry -> onUi(token) { finishSession(message, retry) } },
        ).also { it.start() }
    }

    private fun finishSession(message: String?, retry: Boolean) {
        val keepFrame = message == null && mediaIndex + 1 == mediaItemCount
        stopSession(clearFrame = !keepFrame)
        if (message != null) {
            val source = checkNotNull(playback)
            val groups = playbackAddressGroups(source)
            val audioCount = if (source.audioUrl != null) groups[1].size else 1
            candidate++
            if (retry && candidate / audioCount < groups[mediaIndex].size) {
                if (wantsPlayback) startSession()
            } else {
                wantsPlayback = false
                updateBuffering(false)
                listener?.onError(message)
            }
        } else if (mediaIndex + 1 < mediaItemCount) {
            mediaIndex++
            candidate = 0
            position = 0
            mediaDuration = 0
            if (wantsPlayback) startSession()
        } else {
            ended = true
            buffering = false
            listener?.onPlaybackStateChanged(false, true)
        }
    }

    private fun publishFrame(token: Int, image: Image) {
        if (!framePending.compareAndSet(false, true)) {
            image.close()
            return
        }
        SwingUtilities.invokeLater {
            try {
                if (released || token != generation) {
                    image.close()
                } else {
                    val previous = frame
                    if (previous?.width != image.width || previous?.height != image.height) {
                        listener?.onVideoSizeChanged(image.width.toFloat() / image.height)
                    }
                    frame = image
                    previous?.close()
                }
            } finally {
                framePending.set(false)
            }
        }
    }

    private fun onUi(token: Int, action: () -> Unit) {
        SwingUtilities.invokeLater { if (!released && token == generation) action() }
    }

    private fun updatePlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        listener?.onIsPlayingChanged(value)
    }

    private fun updateBuffering(value: Boolean) {
        if (buffering == value) return
        buffering = value
        listener?.onPlaybackStateChanged(value, false)
    }

    private fun stopSession(clearFrame: Boolean = true) {
        generation++
        session?.close()
        session = null
        if (clearFrame) {
            val previous = frame
            frame = null
            previous?.close()
        }
        updatePlaying(false)
    }

    override fun release() {
        released = true
        listener = null
        stopSession()
        playback = null
    }
}
