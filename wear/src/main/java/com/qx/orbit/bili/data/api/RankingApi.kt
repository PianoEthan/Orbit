package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RankingApi {

    private val api by lazy { BiliApiService.create() }

    internal data class RankingItem(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("owner") val owner: RecommendApi.Owner? = null,
        @SerializedName("stat") val stat: RecommendApi.Stat? = null
    )

    internal data class RankingResponse(
        @SerializedName("list") val list: List<RankingItem>? = null
    )

    suspend fun getRanking(rid: Int, type: String): List<VideoCard> = withContext(Dispatchers.IO) {
        when (val resp = api.getRanking(rid, type)) {
            is Result.Success -> {
                val typeToken = object : TypeToken<ApiResponse<RankingResponse>>() {}.type
                val parsed: ApiResponse<RankingResponse>? = GsonConfig.gson.fromJson(resp.data, typeToken)
                parsed?.data?.list?.filterNotNull()?.map { it.toVideoCard() } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    private fun RankingItem.toVideoCard(): VideoCard = VideoCard(
        title = title ?: "",
        upName = owner?.name ?: "",
        view = StringUtil.toWan(stat?.view?.toLong() ?: 0),
        cover = pic ?: "",
        aid = aid,
        bvid = bvid ?: "",
        cid = cid
    )
}
