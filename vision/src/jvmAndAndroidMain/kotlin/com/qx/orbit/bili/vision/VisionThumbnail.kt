package com.qx.orbit.bili.vision

import java.net.URI
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

internal fun visionThumbnailUrl(url: String, widthPx: Int): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host.orEmpty()
    if (!(host == "hdslb.com" || host.endsWith(".hdslb.com")) || uri.query != null || url.contains('@')) return url
    val width = ((widthPx.coerceIn(160, 1280) + 79) / 80 * 80).coerceAtMost(1280)
    val height = width * 9 / 16
    return "$url@${width}w_${height}h_1c.webp"
}

internal fun ImageRequest.Builder.bilibiliReferer(): ImageRequest.Builder =
    httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
