package com.qx.orbit.bili.core.api

fun mergeCookieString(original: String, newEntries: Map<String, String>): String {
    if (newEntries.isEmpty()) return original
    val cookies = linkedMapOf<String, String>()
    original.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { part ->
        val eqIdx = part.indexOf("=")
        cookies[part.substring(0, eqIdx).trim()] = part.substring(eqIdx + 1).trim()
    }
    newEntries.forEach { (k, v) ->
        val key = k.trim()
        val value = v.trim()
        if (key.isNotEmpty() && value.isNotEmpty()) {
            cookies[key] = value
        }
    }
    return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

fun parseCookieValue(cookie: String, key: String): String {
    cookie.split(";").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.startsWith("$key=")) {
            return trimmed.substring(key.length + 1)
        }
    }
    return ""
}
