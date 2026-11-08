package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WatchLaterApi {

    private val api by lazy { BiliApiService.create() }

    internal data class WatchLaterData(
        @SerializedName("list") val list: List<WatchLaterItem>? = null,
        @SerializedName("count") val count: Int = 0
    )

    internal data class WatchLaterItem(
        @SerializedName("title") val title: String? = null,
        @SerializedName("owner") val owner: Owner? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("stat") val stat: Stat? = null,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("progress") val progress: Int = 0,
        @SerializedName("duration") val duration: Int = 0
    )
    
    internal data class Owner(
        @SerializedName("name") val name: String? = null,
        @SerializedName("mid") val mid: Long = 0
    )

    internal data class Stat(
        @SerializedName("view") val view: Int = 0
    )

    suspend fun getWatchLater(): List<VideoCard> = withContext(Dispatchers.IO) {
        when (val resp = api.getWatchLater()) {
            is Result.Success -> {
                val parsed: ApiResponse<WatchLaterData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<WatchLaterData>>() {}.type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext emptyList()
                
                val videoList = mutableListOf<VideoCard>()
                
                parsed.data.list?.forEach { item ->
                    val isFinished = item.progress == -1 || (item.duration > 0 && item.progress >= item.duration * 0.95)
                    val progressPercent = if (isFinished) {
                        1f
                    } else if (item.duration > 0 && item.progress > 0) {
                        item.progress.toFloat() / item.duration.toFloat()
                    } else {
                        null
                    }
                    
                    val viewText = if (isFinished) "已看完"
                    else if (item.progress > 0) "看到${StringUtil.toTime(item.progress)}"
                    else item.stat?.view?.let { StringUtil.toWan(it.toLong()) } ?: ""
                    
                    var cover = item.pic ?: ""
                    if (cover.startsWith("//")) cover = "https:$cover"
                    else if (cover.startsWith("http://")) cover = cover.replace("http://", "https://")
                    
                    videoList.add(
                        VideoCard(
                            title = item.title ?: "",
                            upName = item.owner?.name ?: "",
                            view = viewText,
                            cover = cover,
                            type = "video",
                            aid = item.aid,
                            bvid = item.bvid ?: "",
                            cid = item.cid,
                            progressPercent = progressPercent,
                            kid = item.aid.toString() // Use aid as kid for deletion in Watch Later
                        )
                    )
                }
                videoList
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun addWatchLater(aid: Long): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.addWatchLater(aid, CookieManager.getCsrf())) {
            is Result.Success -> {
                val parsed = GsonConfig.gson.fromJson(resp.data, ApiResponse::class.java)
                if (parsed.code == 0) ApiResult(code = 0)
                else ApiResult(code = parsed.code, message = parsed.message ?: "")
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }

    suspend fun deleteWatchLater(aid: Long): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.deleteWatchLater(aid, CookieManager.getCsrf())) {
            is Result.Success -> {
                val parsed = GsonConfig.gson.fromJson(resp.data, ApiResponse::class.java)
                if (parsed.code == 0) ApiResult(code = 0)
                else ApiResult(code = parsed.code, message = parsed.message ?: "")
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }
}
