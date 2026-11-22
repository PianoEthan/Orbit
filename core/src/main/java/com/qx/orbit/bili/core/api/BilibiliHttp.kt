package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val BILIBILI_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Safari/537.36"

internal suspend fun OkHttpClient.getBilibiliJson(url: String, cookie: String = ""): JsonObject {
    val request = Request.Builder().url(url)
        .header("Referer", "https://www.bilibili.com/")
        .header("User-Agent", BILIBILI_USER_AGENT)
        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
        .build()
    return executeBilibiliJson(request)
}

internal suspend fun OkHttpClient.postBilibiliForm(url: String, cookie: String, fields: Map<String, String>): JsonObject {
    val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
    val request = Request.Builder().url(url).post(form)
        .header("Referer", "https://www.bilibili.com/")
        .header("User-Agent", BILIBILI_USER_AGENT).header("Cookie", cookie).build()
    return newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build().executeBilibiliJson(request)
}

internal suspend fun OkHttpClient.postBilibiliJson(
    url: String,
    cookie: String,
    body: JsonObject,
    referer: String = "https://www.bilibili.com/",
): JsonObject {
    val request = Request.Builder().url(url)
        .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .header("Referer", referer)
        .header("User-Agent", BILIBILI_USER_AGENT)
        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
        .build()
    return newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build().executeBilibiliJson(request)
}

internal suspend fun OkHttpClient.getBilibiliHtml(url: String, cookie: String = ""): String {
    val request = Request.Builder().url(url)
        .header("Referer", "https://www.bilibili.com/")
        .header("User-Agent", BILIBILI_USER_AGENT)
        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
        .build()
    return executeBilibiliText(request)
}

private suspend fun OkHttpClient.executeBilibiliJson(request: Request): JsonObject {
    val body = executeBilibiliText(request)
    return withContext(Dispatchers.Default) { JsonParser.parseString(body).asJsonObject }
}

private suspend fun OkHttpClient.executeBilibiliText(request: Request): String {
    val body = suspendCancellableCoroutine<String> { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        check(it.isSuccessful) { "HTTP ${it.code}" }
                        it.body.string()
                    }
                    continuation.resume(text)
                } catch (exception: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }
        })
    }
    return body
}

internal fun JsonObject.requireBilibiliData(): JsonObject {
    check(get("code")?.asInt == 0) { get("message")?.asString ?: "接口返回错误" }
    return getAsJsonObject("data") ?: error("接口数据为空")
}
