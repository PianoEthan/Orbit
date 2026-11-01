package com.qx.orbit.bili.presentation.player

import kotlin.math.min

internal data class InteractionContentSize(val width: Float, val height: Float)
internal data class InteractionAnchor(val x: Float, val y: Float)

internal fun interactionContentSize(width: Float, height: Float, videoWidth: Float, videoHeight: Float): InteractionContentSize {
    if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return InteractionContentSize(0f, 0f)
    val scale = min(width / videoWidth, height / videoHeight)
    return InteractionContentSize(videoWidth * scale, videoHeight * scale)
}

internal fun interactionAnchor(x: Double?, y: Double?, sourceWidth: Int, sourceHeight: Int, width: Float, height: Float): InteractionAnchor? {
    if (x == null || y == null || !x.isFinite() || !y.isFinite() || sourceWidth <= 0 || sourceHeight <= 0 ||
        x !in 0.0..sourceWidth.toDouble() || y !in 0.0..sourceHeight.toDouble()) return null
    return InteractionAnchor((x / sourceWidth * width).toFloat(), ((1 - y / sourceHeight) * height).toFloat())
}
