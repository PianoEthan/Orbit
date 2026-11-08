package com.qx.orbit.bili.core.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface DanmakuGateway {
    suspend fun loadXmlDanmaku(cid: Long): ByteArray?
}

class BilibiliDanmakuGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .build(),
    private val cookieProvider: () -> String = { "" },
) : DanmakuGateway {
    override suspend fun loadXmlDanmaku(cid: Long): ByteArray? = withContext(Dispatchers.IO) {
        if (cid <= 0L) return@withContext null
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v1/dm/list.so?oid=$cid")
            .header("User-Agent", BILIBILI_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .apply { cookieProvider().takeIf { it.isNotBlank() }?.let { header("Cookie", it) } }
            .build()
        try {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val bytes = response.use { if (it.isSuccessful) it.body.bytes() else null }
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }
}

fun decompressBilibiliDanmakuStream(bytes: ByteArray): InputStream {
    if (bytes.isEmpty() || bytes.first() == '<'.code.toByte()) return ByteArrayInputStream(bytes)
    if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        return ByteArrayInputStream(GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() })
    }
    for (raw in listOf(true, false)) {
        val inflater = Inflater(raw)
        try {
            val decoded = InflaterInputStream(ByteArrayInputStream(bytes), inflater).use { it.readBytes() }
            return ByteArrayInputStream(decoded)
        } catch (_: IOException) {
        } finally {
            inflater.end()
        }
    }
    return ByteArrayInputStream(bytes)
}
