package com.qx.orbit.bili.util.player.media3

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.qx.orbit.bili.data.model.DashData
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.util.player.OrbitPlayer

@UnstableApi
class Media3PlayerWrapper(context: Context) : OrbitPlayer {

    private val httpDataSourceFactory = OkHttpDataSource.Factory(HttpClient.client)
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com"))
    
    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()

    // DASH data — stored separately from dataSource to avoid overwrite
    private var dashVideoUrl = ""
    private var dashAudioUrl = ""
    private var videoUrl = ""

    // Bandwidth estimate for tcpSpeed
    private var lastBandwidthBytesPerSec = 0L

    // Listener holders
    private var onPreparedListener: OrbitPlayer.OnPreparedListener? = null
    private var onCompletionListener: OrbitPlayer.OnCompletionListener? = null
    private var onErrorListener: OrbitPlayer.OnErrorListener? = null
    private var onInfoListener: OrbitPlayer.OnInfoListener? = null
    private var onVideoSizeChangedListener: OrbitPlayer.OnVideoSizeChangedListener? = null

    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                lastBandwidthBytesPerSec = bitrateEstimate / 8
            }
        })
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        onPreparedListener?.onPrepared(this@Media3PlayerWrapper)
                        onPreparedListener = null
                    }
                    Player.STATE_ENDED -> {
                        onCompletionListener?.onCompletion(this@Media3PlayerWrapper)
                    }
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onErrorListener?.onError(this@Media3PlayerWrapper, error.errorCode, 0)
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                val what = if (isLoading) OrbitPlayer.MEDIA_INFO_BUFFERING_START
                else OrbitPlayer.MEDIA_INFO_BUFFERING_END
                onInfoListener?.onInfo(this@Media3PlayerWrapper, what, 0)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                onVideoSizeChangedListener?.onVideoSizeChanged(
                    this@Media3PlayerWrapper,
                    videoSize.width, videoSize.height,
                    videoSize.pixelWidthHeightRatio.toInt(), 1
                )
            }
        })
    }

    // --- Lifecycle ---

    override fun reset() {
        player.stop()
        player.clearMediaItems()
        dashVideoUrl = ""
        dashAudioUrl = ""
        videoUrl = ""
        onPreparedListener = null
    }

    override fun prepareAsync() {
        val source = buildMediaSource()
        if (source != null) {
            player.setMediaSource(source)
        } else {
            // Fallback: try any available URL
            val fallbackUrl = videoUrl.ifEmpty { dashVideoUrl }
            if (fallbackUrl.isEmpty()) {
                Log.e("Media3Player", "No URL available for playback")
                onErrorListener?.onError(this@Media3PlayerWrapper, -1, 0)
                return
            }
            if (isValidUrl(fallbackUrl)) {
                player.setMediaItem(MediaItem.fromUri(fallbackUrl))
            } else {
                Log.e("Media3Player", "Invalid fallback URL: $fallbackUrl")
                onErrorListener?.onError(this@Media3PlayerWrapper, -1, 0)
                return
            }
        }
        player.prepare()
    }

    override fun start() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(ms: Long) {
        player.seekTo(ms)
    }

    override fun release() {
        try {
            player.clearVideoSurface()
        } catch (_: Exception) {}
        player.release()
    }

    // --- Data source ---

    override var dataSource: String
        get() = videoUrl
        set(value) { videoUrl = value }

    // --- Surface binding ---

    override fun setSurface(surface: Surface?) {
        player.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        if (holder != null) {
            player.setVideoSurfaceHolder(holder)
        } else {
            player.clearVideoSurface()
        }
    }

    // --- State ---

    override val currentPosition: Long
        get() = player.currentPosition

    override val duration: Long
        get() = player.duration.coerceAtLeast(0)

    override val tcpSpeed: Long
        get() = lastBandwidthBytesPerSec

    // --- Speed ---

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    // --- IJK-compatible options ---

    override fun setOption(category: Int, name: String, value: Long) {
        if (category == OrbitPlayer.OPT_CATEGORY_PLAYER && name == "loop") {
            player.repeatMode = if (value == 1L) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    override fun setOption(category: Int, name: String, value: String) {
        // user_agent / headers are handled by HttpClient interceptors
    }

    // --- Listeners ---

    override fun setOnPreparedListener(listener: OrbitPlayer.OnPreparedListener?) {
        onPreparedListener = listener
    }

    override fun setOnCompletionListener(listener: OrbitPlayer.OnCompletionListener?) {
        onCompletionListener = listener
    }

    override fun setOnErrorListener(listener: OrbitPlayer.OnErrorListener?) {
        onErrorListener = listener
    }

    override fun setOnInfoListener(listener: OrbitPlayer.OnInfoListener?) {
        onInfoListener = listener
    }

    override fun setOnVideoSizeChangedListener(listener: OrbitPlayer.OnVideoSizeChangedListener?) {
        onVideoSizeChangedListener = listener
    }

    // --- DASH support ---

    override fun setDashData(dashData: DashData?, videoUrl: String, audioUrl: String) {
        this.dashVideoUrl = videoUrl
        this.dashAudioUrl = audioUrl
    }

    private fun buildMediaSource(): MediaSource? {
        val videoUrl = dashVideoUrl.ifEmpty { videoUrl }
        if (videoUrl.isEmpty()) {
            Log.e("Media3Player", "No video URL available")
            return null
        }
        if (!isValidUrl(videoUrl)) {
            Log.e("Media3Player", "Invalid video URL: $videoUrl")
            return null
        }

        // Merge audio+video if both are valid URLs
        if (dashAudioUrl.isNotEmpty() && isValidUrl(dashAudioUrl) && dashVideoUrl != dashAudioUrl) {
            Log.d("Media3Player", "Merging: video=$dashVideoUrl audio=$dashAudioUrl")
            val videoSrc = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(dashVideoUrl))
            val audioSrc = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(dashAudioUrl))
            return MergingMediaSource(videoSrc, audioSrc)
        }

        Log.d("Media3Player", "Single source: $videoUrl")
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")
    }
}
