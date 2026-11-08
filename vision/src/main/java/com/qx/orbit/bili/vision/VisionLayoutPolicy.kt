package com.qx.orbit.bili.vision

internal fun visionUserGridColumns(contentWidthDp: Float, fontScale: Float): Int =
    if (fontScale >= 1.5f && contentWidthDp < 600f) 1 else visionGridColumns(contentWidthDp / fontScale.coerceIn(1f, 2f))

internal fun visionGridColumns(contentWidthDp: Float): Int = when {
    contentWidthDp < 360f -> 1
    contentWidthDp < 600f -> 2
    else -> (contentWidthDp / 240f).toInt().coerceAtLeast(2)
}
