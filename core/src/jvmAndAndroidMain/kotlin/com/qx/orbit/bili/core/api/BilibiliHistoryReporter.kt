package com.qx.orbit.bili.core.api

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class BilibiliHistoryReporter(private val client: OkHttpClient = OkHttpClient()) : HistoryReporter {
    override suspend fun reportEpisode(video: CoreVideo, progressSeconds: Long, cookie: String, csrf: String) {
        require(video.episodeId > 0 && video.seasonId > 0 && progressSeconds >= 0 && cookie.isNotBlank() && csrf.isNotBlank())
        val response = client.postBilibiliForm("https://api.bilibili.com/x/click-interface/web/heartbeat", cookie, mapOf(
            "aid" to "${video.id}", "cid" to "${video.cid}", "epid" to "${video.episodeId}", "sid" to "${video.seasonId}",
            "type" to "4", "played_time" to "$progressSeconds", "realtime" to "$progressSeconds", "csrf" to csrf))
        check(response.get("code")?.asInt == 0) { "番剧进度同步未完成" }
    }
    override suspend fun report(aid: Long, cid: Long, progressSeconds: Long, cookie: String, csrf: String) {
        require(aid > 0 && cid > 0 && progressSeconds >= 0)
        require(cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val form = FormBody.Builder().add("aid", aid.toString()).add("cid", cid.toString())
            .add("progress", progressSeconds.toString()).add("platform", "android").add("csrf", csrf).build()
        val request = Request.Builder().url("https://api.bilibili.com/x/v2/history/report")
            .header("Referer", "https://www.bilibili.com/").header("User-Agent", BILIBILI_USER_AGENT)
            .header("Cookie", cookie).post(form).build()
        val text = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            check(it.isSuccessful) { "历史同步失败：HTTP ${it.code}" }
                            it.body.string()
                        }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
        val json = JsonParser.parseString(text).asJsonObject
        check(json.get("code")?.asInt == 0) { "历史同步未完成" }
    }
}
