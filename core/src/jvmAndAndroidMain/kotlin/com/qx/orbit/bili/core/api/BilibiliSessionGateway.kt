package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object BilibiliDeviceFingerprint {
    private val cached = AtomicReference<Map<String, String>>(emptyMap())

    fun cache(buvids: Map<String, String>) {
        if (buvids.isNotEmpty()) cached.set(buvids)
    }

    fun clear() {
        cached.set(emptyMap())
    }

    fun current(): Map<String, String> = cached.get()

    fun attachIfMissing(cookie: String): String {
        val buvid3 = parseCookieValue(cookie, "buvid3")
        if (buvid3.isNotBlank()) return cookie
        val currentFingerprint = cached.get()
        if (currentFingerprint.isEmpty()) return cookie
        return mergeCookieString(cookie, currentFingerprint)
    }
}

class BilibiliSessionGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .build(),
    private val cookieProvider: () -> String = { "" },
    private val cookieSaver: (String) -> Unit = {},
) : SessionGateway {
    override suspend fun initializeSession() {
        val current = cookieProvider()
        val buvid3 = parseCookieValue(current, "buvid3")
        if (buvid3.isNotBlank()) return
        val fetched = fetchBuvids() ?: return
        val merged = mergeCookieString(current, fetched)
        if (merged != current) {
            cookieSaver(merged)
        }
    }

    override suspend fun refreshSession() = Unit

    suspend fun fetchBuvids(): Map<String, String>? {
        return try {
            val json = client.getBilibiliJson("https://api.bilibili.com/x/frontend/finger/spi")
            if (json.get("code")?.asInt == 0) {
                val data = json.getAsJsonObject("data")
                val b3 = data?.get("b_3")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val b4 = data?.get("b_4")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                if (b3.isNotBlank()) {
                    val map = mutableMapOf("buvid3" to b3)
                    if (b4.isNotBlank()) map["buvid4"] = b4
                    BilibiliDeviceFingerprint.cache(map)
                    return map
                }
            }
            fallbackBuvid()
        } catch (_: Exception) {
            fallbackBuvid()
        }
    }

    private suspend fun fallbackBuvid(): Map<String, String>? = try {
        val json = client.getBilibiliJson("https://api.bilibili.com/x/web-frontend/getbuvid")
        if (json.get("code")?.asInt == 0) {
            val data = json.getAsJsonObject("data")
            val buvid = data?.get("buvid")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            if (buvid.isNotBlank()) {
                val map = mapOf("buvid3" to buvid)
                BilibiliDeviceFingerprint.cache(map)
                map
            } else null
        } else null
    } catch (_: Exception) {
        null
    }
}

class NoOpSessionGateway : SessionGateway {
    override suspend fun initializeSession() = Unit
    override suspend fun refreshSession() = Unit
}
