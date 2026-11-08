package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.qx.orbit.bili.data.remote.CookieManager

object RecommendApi {

    private val api by lazy { BiliApiService.create() }

    internal data class RecommendResponse(
        @SerializedName("item") val item: List<Item>? = null
    )

    internal data class Item(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("owner") val owner: Owner? = null,
        @SerializedName("stat") val stat: Stat? = null
    )

    internal data class Owner(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null
    )

    internal data class Stat(
        @SerializedName("view") val view: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("danmaku") val danmaku: Int = 0,
        @SerializedName("reply") val reply: Int = 0,
        @SerializedName("favorite") val favorite: Int = 0,
        @SerializedName("coin") val coin: Int = 0,
        @SerializedName("share") val share: Int = 0
    )

    internal data class PopularResponse(
        @SerializedName("list") val list: List<Item>? = null
    )

    suspend fun getRecommend(page: Int): List<VideoCard> = withContext(Dispatchers.IO) {
        val rawUrl = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?fresh_idx=${page}&feed_version=V8&fresh_type=4&ps=10"
        val url = ConfInfoApi.signWBI(rawUrl)
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<RecommendResponse>>() {}.type
        val resp: ApiResponse<RecommendResponse>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        resp.data.item?.filterNotNull()?.filter { !it.bvid.isNullOrEmpty() }?.map { it.toVideoCard() } ?: emptyList()
    }

    suspend fun getRelated(aid: Long): List<VideoCard> = withContext(Dispatchers.IO) {
        when (val resp = api.getRelated(aid)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<List<Item>>>() {}.type
                val apiResp: ApiResponse<List<Item>>? = GsonConfig.gson.fromJson(resp.data, type)
                apiResp?.data?.filterNotNull()?.map { it.toVideoCard() } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun getPopular(page: Int): List<VideoCard> = withContext(Dispatchers.IO) {
        when (val resp = api.getPopular(page)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<PopularResponse>>() {}.type
                val apiResp: ApiResponse<PopularResponse>? = GsonConfig.gson.fromJson(resp.data, type)
                apiResp?.data?.list?.filterNotNull()?.map { it.toVideoCard() } ?: emptyList()
            }
            is Result.Error -> throw resp.exception
        }
    }

    suspend fun getPrecious(page: Int): List<VideoCard> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/web-interface/popular/precious?page=$page&page_size=10"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<PopularResponse>>() {}.type
        val resp: ApiResponse<PopularResponse>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        resp.data.list?.filterNotNull()?.map { it.toVideoCard() } ?: emptyList()
    }

    private fun Item.toVideoCard(): VideoCard = VideoCard(
        title = title ?: "",
        upName = owner?.name ?: "",
        view = StringUtil.toWan(stat?.view?.toLong() ?: 0),
        cover = pic?.let { 
            if (it.startsWith("//")) "https:$it" 
            else if (it.startsWith("http://")) it.replace("http://", "https://") 
            else it 
        } ?: "",
        aid = if (id > 0) id else aid,
        bvid = bvid ?: "",
        cid = cid,
        mid = owner?.mid ?: 0
    )

    suspend fun dislike(aid: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bilibili.com/x/web-interface/index/top/feed/rcmd/dislike?goto=av&id=$aid&reason_id=1&rid=1&tag_id=0"
            val json = httpGet(url)
            val type = object : TypeToken<ApiResponse<Any>>() {}.type
            val resp: ApiResponse<Any>? = GsonConfig.gson.fromJson(json, type)
            resp?.isSuccess == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }
}
