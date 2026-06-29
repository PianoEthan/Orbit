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
        @SerializedName("author") val author: HistoryRef? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("stat") val stat: HistoryStat? = null,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("progress") val progress: Int = 0
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

    suspend fun getHistory(lastResult: ApiResult, videoList: List<VideoCard>): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.getHistory("", lastResult.timestamp, lastResult.business, lastResult.offset)) {
            is Result.Success -> {
                val parsed: ApiResponse<HistoryData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<HistoryData>>() {}.type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext ApiResult(code = parsed?.code ?: -1, message = parsed?.message ?: "")
                ApiResult(
                    code = parsed.code,
                    offset = parsed.data.cursor?.max ?: 0,
                    timestamp = parsed.data.cursor?.view_at ?: 0,
                    business = parsed.data.cursor?.business ?: "",
                    isBottom = parsed.data.cursor?.is_end ?: (parsed.data.list.isNullOrEmpty())
                )
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }
}
