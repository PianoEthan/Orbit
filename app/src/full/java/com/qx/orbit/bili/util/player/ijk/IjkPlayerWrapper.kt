package com.qx.orbit.bili.util.player.ijk

import android.view.Surface
import android.view.SurfaceHolder
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import com.qx.orbit.bili.util.player.OrbitPlayer

class IjkPlayerWrapper : OrbitPlayer {

    private val player = IjkMediaPlayer()

    override fun reset() = player.reset()

    override fun prepareAsync() = player.prepareAsync()

    override fun start() = player.start()

    override fun pause() = player.pause()

    override fun seekTo(ms: Long) = player.seekTo(ms)

    override fun release() {
        try {
            player.setSurface(null)
            player.setDisplay(null)
        } catch (_: Exception) {}
        player.release()
    }

    override var dataSource: String
        get() = player.dataSource
        set(value) { player.dataSource = value }

    override fun setSurface(surface: Surface?) = player.setSurface(surface)

    override fun setDisplay(holder: SurfaceHolder?) = player.setDisplay(holder)

    override val currentPosition: Long
        get() = player.currentPosition

    override val duration: Long
        get() = player.duration

    override val tcpSpeed: Long
        get() = player.tcpSpeed

    override fun setSpeed(speed: Float) = player.setSpeed(speed)

    override fun setOption(category: Int, name: String, value: Long) =
        player.setOption(category, name, value)

    override fun setOption(category: Int, name: String, value: String) =
        player.setOption(category, name, value)

    override fun setOnPreparedListener(listener: OrbitPlayer.OnPreparedListener?) {
        if (listener == null) {
            player.setOnPreparedListener(null)
        } else {
            player.setOnPreparedListener {
                listener.onPrepared(this)
            }
        }
    }

    override fun setOnCompletionListener(listener: OrbitPlayer.OnCompletionListener?) {
        if (listener == null) {
            player.setOnCompletionListener(null)
        } else {
            player.setOnCompletionListener {
                listener.onCompletion(this)
            }
        }
    }

    override fun setOnErrorListener(listener: OrbitPlayer.OnErrorListener?) {
        if (listener == null) {
            player.setOnErrorListener(null)
        } else {
            player.setOnErrorListener { _, what, extra ->
                listener.onError(this, what, extra)
            }
        }
    }

    override fun setOnInfoListener(listener: OrbitPlayer.OnInfoListener?) {
        if (listener == null) {
            player.setOnInfoListener(null)
        } else {
            player.setOnInfoListener { _, what, extra ->
                listener.onInfo(this, what, extra)
            }
        }
    }

    override fun setOnVideoSizeChangedListener(listener: OrbitPlayer.OnVideoSizeChangedListener?) {
        if (listener == null) {
            player.setOnVideoSizeChangedListener(null)
        } else {
            player.setOnVideoSizeChangedListener { _, width, height, sarNum, sarDen ->
                listener.onVideoSizeChanged(this, width, height, sarNum, sarDen)
            }
        }
    }
}
