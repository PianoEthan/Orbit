package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object LikeCoinFavApi {

    private val api by lazy { BiliApiService.create() }

    internal data class RelationData(
        @SerializedName("attention") val attention: Boolean = false,
        @SerializedName("like") val like: Boolean = false,
        @SerializedName("dislike") val dislik: Boolean = false,
        @SerializedName("favorite") val favorite: Boolean = false,
        @SerializedName("coin") val coin: Int = 0
    )

    suspend fun triple(aid: Long): Int {
        return when (val resp = api.triple(aid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun like(aid: Long, likeState: Int): Int {
        return when (val resp = api.like(aid, likeState, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun coin(aid: Long, multiply: Int = 1): Int {
        return when (val resp = api.coin(aid, multiply, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun favorite(aid: Long, fid: Long): Int {
        return when (val resp = api.favorite(aid, fid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun getVideoStats(videoInfo: VideoInfo): Pair<ApiResponse<*>, VideoInfo> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/web-interface/archive/relation?aid=${videoInfo.aid}"
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val typeToken = object : TypeToken<ApiResponse<RelationData>>() {}.type
        val resp: ApiResponse<RelationData>? = GsonConfig.gson.fromJson(json, typeToken)
        val updatedInfo = if (resp != null && resp.isSuccess && resp.data != null) {
            val stats = videoInfo.stats ?: Stats()
            videoInfo.copy(stats = stats.copy(
                followed = resp.data.attention,
                liked = resp.data.like,
                disliked = resp.data.dislik,
                favoured = resp.data.favorite,
                coined = resp.data.coin
            ))
        } else videoInfo
        Pair(resp ?: ApiResponse<Any>(code = -1), updatedInfo)
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
