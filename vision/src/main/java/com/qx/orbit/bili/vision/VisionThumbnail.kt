package com.qx.orbit.bili.vision

import android.net.Uri

internal fun visionThumbnailUrl(url: String, widthPx: Int): String {
    val uri = Uri.parse(url)
    val host = uri.host.orEmpty()
    if (!(host == "hdslb.com" || host.endsWith(".hdslb.com")) || uri.query != null || url.contains('@')) return url
    val width = ((widthPx.coerceIn(160, 1280) + 79) / 80 * 80).coerceAtMost(1280)
    val height = width * 9 / 16
    return "$url@${width}w_${height}h_1c.webp"
}
