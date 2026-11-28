package com.qx.orbit.bili.vision

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import com.qx.orbit.bili.core.api.CorePlayback

internal interface VisionPlayerListener {
    fun onIsPlayingChanged(playing: Boolean)
    fun onPlaybackStateChanged(buffering: Boolean, isEnded: Boolean)
    fun onVideoSizeChanged(aspectRatio: Float)
    fun onError(message: String)
}

internal interface VisionPlayerAdapter {
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

@Composable
internal expect fun visionPlayerFactory(): (VisionPlayerEngine) -> VisionPlayerAdapter

@Composable
internal expect fun VisionPlayerSurface(player: VisionPlayerAdapter, modifier: Modifier)

internal expect fun platformVisionEngine(engine: VisionPlayerEngine): VisionPlayerEngine

internal expect fun visionPlaybackKey(event: KeyEvent): PlaybackKey

internal expect fun visionPlaybackDescription(engine: VisionPlayerEngine, playback: CorePlayback?): String

internal expect val platformVisionEngines: List<VisionPlayerEngine>
