package com.qx.orbit.bili.util.player

import android.view.Surface
import android.view.SurfaceHolder
import com.qx.orbit.bili.data.model.DashData

interface OrbitPlayer {

    // Lifecycle
    fun reset()
    fun prepareAsync()
    fun start()
    fun pause()
    fun seekTo(ms: Long)
    fun release()

    // Data source
    var dataSource: String

    // Surface binding
    fun setSurface(surface: Surface?)
    fun setDisplay(holder: SurfaceHolder?)

    // State
    val currentPosition: Long
    val duration: Long
    val tcpSpeed: Long

    // Speed
    fun setSpeed(speed: Float)

    // IJK-compatible options (ExoPlayer maps what it can, ignores the rest)
    fun setOption(category: Int, name: String, value: Long)
    fun setOption(category: Int, name: String, value: String)

    /** Set DASH stream info for Media3. IJK ignores this (default no-op). */
    fun setDashData(dashData: DashData?, videoUrl: String, audioUrl: String) {}

    // Listeners
    fun setOnPreparedListener(listener: OnPreparedListener?)
    fun setOnCompletionListener(listener: OnCompletionListener?)
    fun setOnErrorListener(listener: OnErrorListener?)
    fun setOnInfoListener(listener: OnInfoListener?)
    fun setOnVideoSizeChangedListener(listener: OnVideoSizeChangedListener?)

    fun interface OnPreparedListener {
        fun onPrepared(player: OrbitPlayer)
    }

    fun interface OnCompletionListener {
        fun onCompletion(player: OrbitPlayer)
    }

    fun interface OnErrorListener {
        fun onError(player: OrbitPlayer, what: Int, extra: Int): Boolean
    }

    fun interface OnInfoListener {
        fun onInfo(player: OrbitPlayer, what: Int, extra: Int): Boolean
    }

    fun interface OnVideoSizeChangedListener {
        fun onVideoSizeChanged(player: OrbitPlayer, width: Int, height: Int, sarNum: Int, sarDen: Int)
    }

    companion object {
        const val MEDIA_INFO_BUFFERING_START = 701
        const val MEDIA_INFO_BUFFERING_END = 702

        const val OPT_CATEGORY_FORMAT = 1
        const val OPT_CATEGORY_CODEC = 2
        const val OPT_CATEGORY_SWS = 3
        const val OPT_CATEGORY_PLAYER = 4
    }
}

class DelegatingOrbitPlayer(var delegate: OrbitPlayer) : OrbitPlayer {
    fun switchTo(newPlayer: OrbitPlayer) {
        val old = delegate
        delegate = newPlayer
        try { old.release() } catch (_: Exception) {}
    }

    override fun reset() = delegate.reset()
    override fun prepareAsync() = delegate.prepareAsync()
    override fun start() = delegate.start()
    override fun pause() = delegate.pause()
    override fun seekTo(ms: Long) = delegate.seekTo(ms)
    override fun release() = delegate.release()
    override var dataSource: String
        get() = delegate.dataSource
        set(value) { delegate.dataSource = value }
    override fun setSurface(surface: Surface?) = delegate.setSurface(surface)
    override fun setDisplay(holder: SurfaceHolder?) = delegate.setDisplay(holder)
    override val currentPosition: Long get() = delegate.currentPosition
    override val duration: Long get() = delegate.duration
    override val tcpSpeed: Long get() = delegate.tcpSpeed
    override fun setSpeed(speed: Float) = delegate.setSpeed(speed)
    override fun setOption(category: Int, name: String, value: Long) = delegate.setOption(category, name, value)
    override fun setOption(category: Int, name: String, value: String) = delegate.setOption(category, name, value)
    override fun setDashData(dashData: DashData?, videoUrl: String, audioUrl: String) = delegate.setDashData(dashData, videoUrl, audioUrl)
    override fun setOnPreparedListener(listener: OrbitPlayer.OnPreparedListener?) = delegate.setOnPreparedListener(listener)
    override fun setOnCompletionListener(listener: OrbitPlayer.OnCompletionListener?) = delegate.setOnCompletionListener(listener)
    override fun setOnErrorListener(listener: OrbitPlayer.OnErrorListener?) = delegate.setOnErrorListener(listener)
    override fun setOnInfoListener(listener: OrbitPlayer.OnInfoListener?) = delegate.setOnInfoListener(listener)
    override fun setOnVideoSizeChangedListener(listener: OrbitPlayer.OnVideoSizeChangedListener?) = delegate.setOnVideoSizeChangedListener(listener)
}
