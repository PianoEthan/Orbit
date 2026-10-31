package com.qx.orbit.bili.presentation.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

internal enum class PlayerDragGesture { None, Zoom, Pan, Seek, Cancel }

internal class PlayerPointerSequence(initialPointer: Long) {
    var activePointer: Long? = initialPointer
        private set
    var isTransform = false
        private set

    fun update(pressedIds: List<Long>) {
        if (pressedIds.size > 1) isTransform = true
        if (isTransform && activePointer !in pressedIds) activePointer = pressedIds.firstOrNull()
    }
}

internal fun playerDragGesture(
    dx: Float,
    dy: Float,
    slop: Float,
    secondTap: Boolean,
    zoomEnabled: Boolean,
    oneFingerZoomEnabled: Boolean,
    canPan: Boolean,
    canSeek: Boolean
): PlayerDragGesture {
    if (dx * dx + dy * dy <= slop * slop) return PlayerDragGesture.None
    if (secondTap) {
        return if (zoomEnabled && oneFingerZoomEnabled && abs(dy) > abs(dx)) {
            PlayerDragGesture.Zoom
        } else PlayerDragGesture.Cancel
    }
    if (canSeek && abs(dx) > abs(dy)) return PlayerDragGesture.Seek
    return if (zoomEnabled && canPan) PlayerDragGesture.Pan else PlayerDragGesture.Cancel
}

internal fun oneFingerZoomFactor(deltaDp: Float): Float = exp(deltaDp / 160f)

internal fun distanceSeekWindowMs(durationMs: Long, distanceDp: Float): Double {
    val nearWindow = min(durationMs.coerceAtLeast(0L).toDouble(), 30_000.0)
    val fraction = (distanceDp.coerceAtLeast(0f) / 120f).coerceIn(0f, 1f)
    return nearWindow + (durationMs.coerceAtLeast(0L) - nearWindow) * fraction
}

internal class DistanceSeekSession(
    private val durationMs: Long,
    initialPositionMs: Long,
    private val barWidthPx: Float
) {
    private var positionMs = initialPositionMs.toDouble().coerceIn(0.0, durationMs.coerceAtLeast(0L).toDouble())
    val targetMs: Long get() = positionMs.toLong()

    fun drag(deltaX: Float, distanceDp: Float): Long {
        if (barWidthPx > 0f && durationMs > 0 && deltaX.isFinite()) {
            positionMs = (positionMs + deltaX / barWidthPx * distanceSeekWindowMs(durationMs, distanceDp))
                .coerceIn(0.0, durationMs.toDouble())
        }
        return targetMs
    }
}

internal fun effectivePlaybackSpeed(preferred: Float, temporary: Boolean): Float =
    if (temporary) 2f else preferred

internal fun shouldRunDanmaku(
    foregroundVideo: Boolean,
    enabled: Boolean,
    prepared: Boolean,
    playing: Boolean,
    seeking: Boolean,
    buffering: Boolean
): Boolean = foregroundVideo && enabled && prepared && playing && !seeking && !buffering
