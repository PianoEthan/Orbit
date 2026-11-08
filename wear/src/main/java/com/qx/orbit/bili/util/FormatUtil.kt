package com.qx.orbit.bili.util

fun formatCount(count: Int): String {
    return if (count >= 10000) {
        String.format("%.1f万", count / 10000f)
    } else {
        count.toString()
    }
}

fun String.fixCoverUrl(): String {
    return when {
        this.startsWith("//") -> "https:$this"
        this.startsWith("http://") -> this.replaceFirst("http://", "https://")
        else -> this
    }.replace(".avif", ".webp")
}
