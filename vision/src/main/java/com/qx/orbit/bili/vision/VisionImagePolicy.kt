package com.qx.orbit.bili.vision

internal fun visionImageSources(urls: List<String>): List<String> = urls.filter { it.startsWith("https://") }.distinct().take(100)

internal fun visionImagePage(current: Int, delta: Int, count: Int): Int =
    if (count <= 0) 0 else (current.toLong() + delta).coerceIn(0L, (count - 1).toLong()).toInt()

internal fun visionImagePan(value: Float, viewport: Float, scale: Float): Float {
    val limit = viewport.coerceAtLeast(0f) * (scale.coerceIn(1f, 4f) - 1f) / 2
    return value.coerceIn(-limit, limit)
}
