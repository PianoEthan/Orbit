package com.qx.orbit.bili.presentation.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

internal data class PlayerGestureCallbacks(
    val zoomEnabled: Boolean = true,
    val oneFingerZoomEnabled: Boolean = true,
    val canPan: Boolean = false,
    val canSeek: Boolean = false,
    val canLongPress: Boolean = false,
    val onTap: () -> Unit = {},
    val onDoubleTap: () -> Unit = {},
    val onLongPress: (Boolean) -> Unit = {},
    val onTransform: (Offset, Float) -> Unit = { _, _ -> },
    val onSeekStart: () -> Unit = {},
    val onSeekDrag: (Float, Offset) -> Unit = { _, _ -> },
    val onSeekEnd: (Boolean) -> Unit = {}
)

@Composable
internal fun Modifier.playerGestures(callbacks: PlayerGestureCallbacks): Modifier {
    val latest by rememberUpdatedState(callbacks)
    val density = LocalDensity.current.density
    val view = LocalView.current
    return pointerInput(density) {
        awaitEachGesture {
            val parent = view.parent
            var seeking = false
            var holding = false
            suspend fun AwaitPointerEventScope.track(down: PointerInputChange, secondTap: Boolean): Boolean {
                var gesture = PlayerDragGesture.None
                val pointers = PlayerPointerSequence(down.id.value)
                var transforming = false
                var transformPan = Offset.Zero
                var transformZoom = 1f
                var lastPosition = down.position
                val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                while (true) {
                    val remaining = deadline - currentEvent.changes.maxOf { it.uptimeMillis }
                    val waitingForHold = !secondTap && !holding && gesture == PlayerDragGesture.None
                    val event = if (waitingForHold) {
                        if (remaining <= 0L) null else withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    } else awaitPointerEvent()
                    if (event == null) {
                        if (latest.canLongPress) {
                            holding = true
                            latest.onLongPress(true)
                        }
                        continue
                    }
                    val pressedIds = event.changes.filter { it.pressed }.map { it.id.value }
                    pointers.update(pressedIds)
                    val pressed = pressedIds.size
                    if (pressed > 1) {
                        if (seeking) {
                            latest.onSeekEnd(false)
                            seeking = false
                        }
                        if (holding) {
                            latest.onLongPress(false)
                            holding = false
                        }
                        gesture = PlayerDragGesture.Cancel
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        transformPan += pan
                        transformZoom *= zoom
                        if (!transforming) {
                            transforming = transformPan.getDistance() > viewConfiguration.touchSlop ||
                                abs(1f - transformZoom) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop
                        }
                        if (latest.zoomEnabled && transforming) {
                            latest.onTransform(pan, zoom)
                            event.changes.forEach { it.consume() }
                        }
                        continue
                    }
                    if (pointers.isTransform) {
                        if (pointers.activePointer == null) return false
                        if (latest.zoomEnabled && transforming) {
                            latest.onTransform(event.calculatePan(), 1f)
                            event.changes.forEach { it.consume() }
                        }
                        continue
                    }
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: return false
                    val displacement = pointer.position - down.position
                    if (!pointer.pressed) {
                        val tap = gesture == PlayerDragGesture.None && !holding &&
                            displacement.getDistance() <= viewConfiguration.touchSlop &&
                            pointer.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                        if (seeking) {
                            latest.onSeekDrag(pointer.position.x - lastPosition.x, pointer.position)
                            latest.onSeekEnd(true)
                            seeking = false
                        }
                        val consumedByChild = pointer.isConsumed
                        if (tap && !consumedByChild) {
                            pointer.consume()
                            return true
                        }
                        return false
                    }
                    if (holding && displacement.getDistance() > viewConfiguration.touchSlop) return false
                    if (gesture == PlayerDragGesture.None && !holding) {
                        gesture = playerDragGesture(
                            displacement.x, displacement.y, viewConfiguration.touchSlop,
                            secondTap, latest.zoomEnabled, latest.oneFingerZoomEnabled,
                            latest.canPan, latest.canSeek
                        )
                        if (gesture == PlayerDragGesture.Seek) {
                            seeking = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            latest.onSeekStart()
                        } else if (gesture == PlayerDragGesture.Pan || gesture == PlayerDragGesture.Zoom) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    val delta = pointer.position - lastPosition
                    when (gesture) {
                        PlayerDragGesture.Zoom -> {
                            latest.onTransform(Offset.Zero, oneFingerZoomFactor(delta.y / density))
                            pointer.consume()
                        }
                        PlayerDragGesture.Pan -> {
                            latest.onTransform(delta, 1f)
                            pointer.consume()
                        }
                        PlayerDragGesture.Seek -> {
                            latest.onSeekDrag(delta.x, pointer.position)
                            pointer.consume()
                        }
                        else -> if (holding) pointer.consume()
                    }
                    lastPosition = pointer.position
                }
            }
            try {
                val first = awaitFirstDown(requireUnconsumed = false)
                if (track(first, false)) {
                    val firstUp = currentEvent.changes.firstOrNull { it.id == first.id } ?: first
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (second == null) {
                        latest.onTap()
                    } else {
                        val interval = second.uptimeMillis - firstUp.uptimeMillis
                        val nearby = (second.position - firstUp.position).getDistance() <= viewConfiguration.touchSlop * 4f
                        val doubleTap = interval >= viewConfiguration.doubleTapMinTimeMillis && nearby
                        if (!doubleTap) latest.onTap()
                        if (track(second, doubleTap)) {
                            if (doubleTap) latest.onDoubleTap() else latest.onTap()
                        }
                    }
                }
            } finally {
                if (seeking) latest.onSeekEnd(false)
                if (holding) latest.onLongPress(false)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
