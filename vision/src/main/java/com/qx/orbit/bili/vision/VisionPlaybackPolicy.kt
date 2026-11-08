package com.qx.orbit.bili.vision

internal val visionPlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun supportedPlaybackSpeed(speed: Float): Float = speed.takeIf { it in visionPlaybackSpeeds } ?: 1f

internal fun playbackSwitchPosition(currentCid: Long, targetCid: Long, snapshot: Pair<Int, Long>): Pair<Int, Long> =
    if (currentCid != targetCid) 0 to 0L else snapshot

internal fun playbackSegmentPosition(positionMs: Long, durationsMs: List<Long>): Pair<Int, Long> {
    if (durationsMs.isEmpty() || durationsMs.any { it <= 0 }) return 0 to positionMs.coerceAtLeast(0)
    var remaining = positionMs.coerceAtLeast(0)
    durationsMs.forEachIndexed { index, duration ->
        if (remaining < duration || index == durationsMs.lastIndex) return index to remaining.coerceAtMost(duration)
        remaining -= duration
    }
    return 0 to 0L
}

internal fun playbackShouldRun(userWantsPlayback: Boolean, foreground: Boolean, ready: Boolean): Boolean =
    userWantsPlayback && foreground && ready

internal fun seekPlaybackPosition(position: Long, delta: Long, duration: Long): Long =
    (position + delta).coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }

internal data class VideoViewport(val width: Float, val height: Float)

internal fun fitVideoViewport(width: Float, height: Float, aspectRatio: Float): VideoViewport {
    if (width <= 0f || height <= 0f) return VideoViewport(0f, 0f)
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    return if (width / height > ratio) VideoViewport(height * ratio, height)
    else VideoViewport(width, width / ratio)
}

internal enum class PlaybackKey {
    CONFIRM, LEFT, RIGHT, UP, DOWN, PLAY_PAUSE, PLAY, PAUSE, FAST_FORWARD, REWIND, OTHER,
}

internal enum class PlaybackKeyAction {
    PASS, SHOW_CONTROLS, TOGGLE, PLAY, PAUSE, SEEK_FORWARD, SEEK_BACKWARD,
}

internal fun playbackKeyAction(key: PlaybackKey, controlsVisible: Boolean): PlaybackKeyAction = when (key) {
    PlaybackKey.PLAY_PAUSE -> PlaybackKeyAction.TOGGLE
    PlaybackKey.PLAY -> PlaybackKeyAction.PLAY
    PlaybackKey.PAUSE -> PlaybackKeyAction.PAUSE
    PlaybackKey.FAST_FORWARD -> PlaybackKeyAction.SEEK_FORWARD
    PlaybackKey.REWIND -> PlaybackKeyAction.SEEK_BACKWARD
    PlaybackKey.LEFT -> if (controlsVisible) PlaybackKeyAction.PASS else PlaybackKeyAction.SEEK_BACKWARD
    PlaybackKey.RIGHT -> if (controlsVisible) PlaybackKeyAction.PASS else PlaybackKeyAction.SEEK_FORWARD
    PlaybackKey.CONFIRM, PlaybackKey.UP, PlaybackKey.DOWN ->
        if (controlsVisible) PlaybackKeyAction.PASS else PlaybackKeyAction.SHOW_CONTROLS
    PlaybackKey.OTHER -> PlaybackKeyAction.PASS
}
