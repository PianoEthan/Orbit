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
        @SerializedName("list") val list: List<WatchLaterItem>? = null
    )

    internal data class WatchLaterItem(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("owner") val owner: WatchLaterOwner? = null,
        @SerializedName("stat") val stat: WatchLaterStat? = null,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0
    )

    internal data class WatchLaterOwner(
        @SerializedName("name") val name: String? = null
    )

    internal data class WatchLaterStat(
        @SerializedName("view") val view: Int = 0
    )

    suspend fun getWatchLaterList(): List<VideoCard> = withContext(Dispatchers.IO) {
        when (val resp = api.getWatchLater()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<WatchLaterData>>() {}.type
                val parsed: ApiResponse<WatchLaterData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext emptyList()
                parsed.data.list?.filterNotNull()?.map {
                    VideoCard(title = it.title ?: "", upName = it.owner?.name ?: "", view = StringUtil.toWan(it.stat?.view?.toLong() ?: 0), cover = it.pic ?: "", aid = it.aid, bvid = it.bvid ?: "", cid = it.cid)
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun delete(aid: Long): Int = withContext(Dispatchers.IO) {
        when (val resp = api.deleteWatchLater(aid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun add(aid: Long): Int = withContext(Dispatchers.IO) {
        when (val resp = api.addWatchLater(aid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }
}
