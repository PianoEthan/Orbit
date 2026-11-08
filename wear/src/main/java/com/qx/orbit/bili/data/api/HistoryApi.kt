package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistoryApi {

    private val api by lazy { BiliApiService.create() }

    internal data class HistoryData(
        @SerializedName("list") val list: List<HistoryItem>? = null,
        @SerializedName("cursor") val cursor: CursorData? = null
    )

    internal data class HistoryItem(
        @SerializedName("title") val title: String? = null,
        @SerializedName("author_name") val author_name: String? = null,
        @SerializedName("author") val author: HistoryRef? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("covers") val covers: List<String>? = null,
        @SerializedName("stat") val stat: HistoryStat? = null,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("progress") val progress: Int = 0,
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("kid") val kid: Long = 0,
        @SerializedName("history") val history: HistoryDetail? = null
    )

    internal data class HistoryDetail(
        @SerializedName("oid") val oid: Long = 0,
        @SerializedName("epid") val epid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("business") val business: String? = null
    )

    internal data class HistoryRef(
        @SerializedName("name") val name: String? = null
    )

    internal data class HistoryStat(
        @SerializedName("view") val view: Int = 0
    )

    internal data class CursorData(
        @SerializedName("max") val max: Long = 0,
        @SerializedName("view_at") val view_at: Long = 0,
        @SerializedName("business") val business: String? = null,
        @SerializedName("is_end") val is_end: Boolean = false
    )

    suspend fun reportHistory(aid: Long, cid: Long, progress: Long, privacy: Boolean = false) = withContext(Dispatchers.IO) {
        if (privacy) return@withContext
        api.reportHistory(aid, cid, progress, CookieManager.getCsrf())
    }

    suspend fun getHistory(lastResult: ApiResult, videoList: MutableList<VideoCard>): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.getHistory("", lastResult.timestamp, lastResult.business, lastResult.offset)) {
            is Result.Success -> {
                val parsed: ApiResponse<HistoryData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<HistoryData>>() {}.type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext ApiResult(code = parsed?.code ?: -1, message = parsed?.message ?: "")
                
                parsed.data.list?.forEach { item ->
                    val bvid = item.bvid ?: item.history?.bvid ?: ""
                    val aid = item.history?.oid ?: item.aid
                    val type = when (item.history?.business) {
                        "archive" -> "video"
                        "pgc" -> "bangumi"
                        "article", "article-list" -> "article"
                        else -> item.history?.business ?: "video"
                    }
                    val isVideoOrBangumi = type == "video" || type == "bangumi"
                    
                    val isFinished = item.progress == -1 || (item.duration > 0 && item.progress >= item.duration * 0.95)
                    val progressPercent = if (isVideoOrBangumi) {
                        if (isFinished) {
                            1f
                        } else if (item.duration > 0 && item.progress > 0) {
                            item.progress.toFloat() / item.duration.toFloat()
                        } else {
                            null
                        }
                    } else null
                    
                    val viewText = if (isVideoOrBangumi) {
                        if (isFinished) "已看完"
                        else "看到${StringUtil.toTime(item.progress)}"
                    } else {
                        item.stat?.view?.let { StringUtil.toWan(it.toLong()) } ?: ""
                    }
                    
                    var cover = if (item.covers?.isNotEmpty() == true) item.covers.first() else item.cover ?: ""
                    if (cover.startsWith("//")) cover = "https:$cover"
                    else if (cover.startsWith("http://")) cover = cover.replace("http://", "https://")
                    
                    videoList.add(
                        VideoCard(
                            title = item.title ?: "",
                            upName = item.author_name ?: item.author?.name ?: "",
                            view = viewText,
                            cover = cover,
                            type = type,
                            aid = aid,
                            bvid = bvid,
                            cid = item.history?.cid ?: item.cid,
                            seasonId = if (type == "bangumi") item.kid else 0,
                            progressPercent = progressPercent,
                            kid = "${item.history?.business ?: "archive"}_${item.history?.oid ?: item.kid}"
                        )
                    )
                }

                val max = parsed.data.cursor?.max ?: 0L
                val isEnd = parsed.data.list.isNullOrEmpty() || max == 0L

                ApiResult(
                    code = parsed.code,
                    offset = max,
                    timestamp = parsed.data.cursor?.view_at ?: 0,
                    business = parsed.data.cursor?.business ?: "",
                    isBottom = isEnd
                )
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }

    suspend fun deleteHistory(kid: String): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.deleteHistory(kid, CookieManager.getCsrf())) {
            is Result.Success -> {
                val parsed = GsonConfig.gson.fromJson<ApiResponse<Any>>(resp.data, object : TypeToken<ApiResponse<Any>>() {}.type)
                if (parsed.code == 0) ApiResult(code = 0)
                else ApiResult(code = parsed.code, message = parsed.message ?: "")
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }
}
