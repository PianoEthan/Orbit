package com.qx.orbit.bili.vision

import android.content.Context
import android.view.SurfaceView
import com.qx.orbit.bili.core.api.CorePlayback

internal class VisionAdaptivePlayerAdapter(private val context: Context) : AndroidVisionPlayerAdapter {
    private var delegate: AndroidVisionPlayerAdapter? = null
    private var dash = false
    private var surface: SurfaceView? = null
    private var listener: VisionPlayerListener? = null
    private var speed = 1f
    private var wantsPlayback = false

    override fun bindSurfaceView(surfaceView: SurfaceView) {
        surface = surfaceView
        delegate?.bindSurfaceView(surfaceView)
    }
    override fun setListener(listener: VisionPlayerListener?) {
        this.listener = listener
        delegate?.setListener(listener)
    }
    override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) {
        val needsDash = playback.audioUrl != null
        if (delegate == null || dash != needsDash) {
            delegate?.setListener(null)
            delegate?.release()
            dash = needsDash
            delegate = if (needsDash) VisionMedia3PlayerAdapter(context) else VisionIjkPlayerAdapter()
            delegate?.setListener(listener)
            surface?.let { delegate?.bindSurfaceView(it) }
            delegate?.setPlaybackSpeed(speed)
        }
        val current = checkNotNull(delegate)
        current.prepare(playback, segment, positionMs)
        if (wantsPlayback) current.play() else current.pause()
    }
    override fun play() { wantsPlayback = true; delegate?.play() }
    override fun pause() { wantsPlayback = false; delegate?.pause() }
    override fun seekTo(positionMs: Long) { delegate?.seekTo(positionMs) }
    override fun setPlaybackSpeed(speed: Float) { this.speed = speed; delegate?.setPlaybackSpeed(speed) }
    override fun release() { delegate?.release(); delegate = null; surface = null; listener = null }
    override val currentPosition: Long get() = delegate?.currentPosition ?: 0L
    override val duration: Long get() = delegate?.duration ?: 0L
    override val currentMediaItemIndex: Int get() = delegate?.currentMediaItemIndex ?: 0
    override val mediaItemCount: Int get() = delegate?.mediaItemCount ?: 0
    override val segmentDurationsMs: List<Long> get() = delegate?.segmentDurationsMs.orEmpty()
    override val isPlaying: Boolean get() = delegate?.isPlaying == true
}
