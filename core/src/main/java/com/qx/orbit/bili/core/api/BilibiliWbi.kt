package com.qx.orbit.bili.core.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.security.MessageDigest

internal suspend fun OkHttpClient.signedBilibiliUrl(endpoint: String, parameters: Map<String, String>): String {
    val nav = getBilibiliJson("https://api.bilibili.com/x/web-interface/nav")
    val images = nav.getAsJsonObject("data")?.getAsJsonObject("wbi_img") ?: error("无法获取接口签名")
    val rawKey = listOf("img_url", "sub_url").joinToString("") {
        images.get(it).asString.substringAfterLast('/').substringBefore('.')
    }
    require(rawKey.length >= 64) { "接口签名无效" }
    val key = MIXIN_ORDER.map { rawKey[it] }.joinToString("")
    val params = (parameters + ("wts" to (System.currentTimeMillis() / 1000).toString())).toSortedMap()
    val query = params.entries.joinToString("&") { (name, value) ->
        val clean = value.filterNot { it in "!'()*" }
        "${encode(name)}=${encode(clean)}"
    }
    val digest = MessageDigest.getInstance("MD5").digest((query + key).toByteArray())
        .joinToString("") { "%02x".format(it) }
    return endpoint.toHttpUrl().newBuilder().encodedQuery("$query&w_rid=$digest").build().toString()
}

private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%7E", "~")

private val MIXIN_ORDER = intArrayOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
    27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
)
