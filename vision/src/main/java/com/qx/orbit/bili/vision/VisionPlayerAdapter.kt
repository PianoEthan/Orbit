package com.qx.orbit.bili.vision

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.qx.orbit.bili.core.api.CorePlayback
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

internal interface VisionPlayerListener {
    fun onIsPlayingChanged(playing: Boolean)
    fun onPlaybackStateChanged(buffering: Boolean, isEnded: Boolean)
    fun onVideoSizeChanged(aspectRatio: Float)
    fun onError(message: String)
}

internal interface VisionPlayerAdapter {
    fun bindSurfaceView(surfaceView: SurfaceView)
    fun prepare(playback: CorePlayback, segment: Int, positionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun release()
    val currentPosition: Long
    val duration: Long
    val currentMediaItemIndex: Int
    val mediaItemCount: Int
    val segmentDurationsMs: List<Long>
    val isPlaying: Boolean
    fun setListener(listener: VisionPlayerListener?)
}

internal fun supportsVisionIjk(abis: List<String>): Boolean = abis.any { it == "arm64-v8a" || it == "armeabi-v7a" }

internal fun effectiveVisionEngine(requested: VisionPlayerEngine, abis: List<String> = Build.SUPPORTED_ABIS.toList()): VisionPlayerEngine =
    if (requested == VisionPlayerEngine.IJK && !supportsVisionIjk(abis)) VisionPlayerEngine.MEDIA3 else requested

internal fun createVisionPlayerAdapter(context: Context, engine: VisionPlayerEngine): VisionPlayerAdapter =
    when (engine) {
        VisionPlayerEngine.IJK -> VisionIjkPlayerAdapter()
        VisionPlayerEngine.MEDIA3 -> VisionMedia3PlayerAdapter(context)
    }

internal class VisionIjkPlayerAdapter : VisionPlayerAdapter {
    private var player: IjkMediaPlayer? = null
    private var listener: VisionPlayerListener? = null
    private var surfaceView: SurfaceView? = null
    private var playback: CorePlayback? = null
    private var candidateIndex = 0
    private var pendingPosition = 0L
    private var speed = 1f
    private var prepared = false
    private var wantsPlayback = false
    private var buffering = false
    private var released = false
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    override var currentMediaItemIndex = 0
        private set
    override val mediaItemCount: Int get() = playback?.urls?.size ?: 0
    override val segmentDurationsMs: List<Long> get() = playback?.segmentDurationsMs.orEmpty()
    override val currentPosition: Long get() = if (prepared) player?.currentPosition?.coerceAtLeast(0) ?: pendingPosition else pendingPosition
    override val duration: Long get() = if (prepared) player?.duration?.coerceAtLeast(0) ?: 0L else 0L
    override val isPlaying: Boolean get() = prepared && wantsPlayback && !buffering && player?.isPlaying == true

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) { player?.setDisplay(holder) }
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { player?.setDisplay(holder) }
        override fun surfaceDestroyed(holder: SurfaceHolder) { player?.setDisplay(null) }
    }

    override fun bindSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView?.holder?.removeCallback(surfaceCallback)
        this.surfaceView = surfaceView
        surfaceView.holder.addCallback(surfaceCallback)
        if (surfaceView.holder.surface.isValid) player?.setDisplay(surfaceView.holder)
    }

    override fun setListener(listener: VisionPlayerListener?) { this.listener = listener }

    override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) {
        check(playback.audioUrl == null) { "ijkPlayer 需要音视频合一播放流，请切换 ExoPlayer 播放 DASH" }
        require(playback.urls.isNotEmpty())
        this.playback = playback
        currentMediaItemIndex = segment.coerceIn(playback.urls.indices)
        pendingPosition = positionMs.coerceAtLeast(0)
        candidateIndex = 0
        startCandidate()
    }

    private fun notifyState() {
        listener?.onPlaybackStateChanged(buffering, false)
        listener?.onIsPlayingChanged(isPlaying)
    }

    private fun disposeNative() {
        val old = player
        player = null
        prepared = false
        if (old != null) {
            old.setOnPreparedListener(null)
            old.setOnCompletionListener(null)
            old.setOnErrorListener(null)
            old.setOnInfoListener(null)
            old.setOnVideoSizeChangedListener(null)
            try { old.setDisplay(null) } finally { old.release() }
        }
    }

    private fun startCandidate() {
        if (released) return
        val current = playback ?: return
        val candidates = playbackAddressGroups(current)[currentMediaItemIndex]
        val token = ++generation
        disposeNative()
        if (candidateIndex >= candidates.size) {
            buffering = false
            wantsPlayback = false
            notifyState()
            listener?.onError("ijkPlayer 播放失败：当前分段的可用地址已全部尝试")
            return
        }
        buffering = true
        notifyState()
        try {
            val ijk = IjkMediaPlayer()
            player = ijk
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0 Chrome/122 Safari/537.36")
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", "Referer: https://www.bilibili.com/\r\n")
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
            ijk.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 15000000L)
            ijk.setOnPreparedListener {
                if (token == generation && !released) {
                    prepared = true
                    buffering = false
                    ijk.setSpeed(speed)
                    if (pendingPosition > 0) ijk.seekTo(pendingPosition)
                    if (wantsPlayback) ijk.start()
                    notifyState()
                }
            }
            ijk.setOnInfoListener { _, what, _ ->
                if (token == generation && !released) {
                    when (what) {
                        IMediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                        IMediaPlayer.MEDIA_INFO_BUFFERING_END -> buffering = false
                    }
                    notifyState()
                }
                true
            }
            ijk.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
                if (token == generation && !released && width > 0 && height > 0) {
                    val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
                    listener?.onVideoSizeChanged(width * sar / height)
                }
            }
            ijk.setOnCompletionListener {
                if (token == generation && !released) {
                    if (currentMediaItemIndex + 1 < mediaItemCount) {
                        currentMediaItemIndex++
                        candidateIndex = 0
                        pendingPosition = 0
                        handler.post { if (token == generation && !released) startCandidate() }
                    } else {
                        wantsPlayback = false
                        listener?.onIsPlayingChanged(false)
                        listener?.onPlaybackStateChanged(false, true)
                    }
                }
            }
            ijk.setOnErrorListener { _, _, _ ->
                if (token == generation && !released) {
                    pendingPosition = currentPosition
                    candidateIndex++
                    handler.post { if (token == generation && !released) startCandidate() }
                }
                true
            }
            surfaceView?.holder?.takeIf { it.surface.isValid }?.let(ijk::setDisplay)
            ijk.dataSource = candidates[candidateIndex]
            ijk.prepareAsync()
        } catch (_: Exception) {
            candidateIndex++
            handler.post { if (token == generation && !released) startCandidate() }
        }
    }

    override fun play() {
        wantsPlayback = true
        if (prepared) player?.start()
        notifyState()
    }
    override fun pause() {
        wantsPlayback = false
        if (prepared) player?.pause()
        notifyState()
    }
    override fun seekTo(positionMs: Long) {
        pendingPosition = positionMs.coerceAtLeast(0)
        if (prepared) player?.seekTo(pendingPosition)
    }
    override fun setPlaybackSpeed(speed: Float) {
        this.speed = supportedPlaybackSpeed(speed)
        if (prepared) player?.setSpeed(this.speed)
    }
    override fun release() {
        released = true
        generation++
        handler.removeCallbacksAndMessages(null)
        surfaceView?.holder?.removeCallback(surfaceCallback)
        listener = null
        disposeNative()
    }
}

@OptIn(UnstableApi::class)
internal class VisionMedia3PlayerAdapter(context: Context) : VisionPlayerAdapter {
    private val source = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 Chrome/122 Safari/537.36")
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/")))
    private val player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
    private var listener: VisionPlayerListener? = null
    private var playback: CorePlayback? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { listener?.onIsPlayingChanged(isPlaying) }
            override fun onPlaybackStateChanged(state: Int) {
                listener?.onPlaybackStateChanged(state == Player.STATE_BUFFERING, state == Player.STATE_ENDED)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    listener?.onVideoSizeChanged(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }
            override fun onPlayerError(error: PlaybackException) { listener?.onError("ExoPlayer 播放异常 (${error.errorCodeName})，当前流的可用地址已尝试") }
        })
    }
    override fun bindSurfaceView(surfaceView: SurfaceView) { player.setVideoSurfaceView(surfaceView) }
    override fun setListener(listener: VisionPlayerListener?) { this.listener = listener }
    override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) {
        this.playback = playback
        player.setMediaSources(buildVisionMediaSources(playback, source))
        player.seekTo(segment.coerceIn(playback.urls.indices), positionMs)
        player.prepare()
    }
    override fun play() { player.playWhenReady = true }
    override fun pause() { player.playWhenReady = false }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs) }
    override fun setPlaybackSpeed(speed: Float) { player.setPlaybackSpeed(supportedPlaybackSpeed(speed)) }
    override fun release() { listener = null; player.release() }
    override val currentPosition: Long get() = player.currentPosition.coerceAtLeast(0)
    override val duration: Long get() = player.duration.coerceAtLeast(0)
    override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex.coerceAtLeast(0)
    override val mediaItemCount: Int get() = player.mediaItemCount
    override val segmentDurationsMs: List<Long> get() = (0 until mediaItemCount).map { index ->
        val timeline = player.currentTimeline
        val duration = if (index < timeline.windowCount) timeline.getWindow(index, Timeline.Window()).durationMs else -1L
        if (duration > 0) duration else playback?.segmentDurationsMs?.getOrNull(index) ?: -1L
    }
    override val isPlaying: Boolean get() = player.isPlaying
}
