package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CreativeCenterApi {

    private val api by lazy { BiliApiService.create() }

    internal data class ScrollsData(
        @SerializedName("be_up") val be_up: BeUpData? = null
    )

    internal data class BeUpData(
        @SerializedName("be_up_minutes") val be_up_minutes: Int = 0,
        @SerializedName("be_up_stat") val be_up_stat: Int = 0
    )

    data class VideoStatData(
        @SerializedName("total_click") val total_click: Int = 0,
        @SerializedName("total_fan") val total_fan: Int = 0,
        @SerializedName("total_like") val total_like: Int = 0
    )

    suspend fun getVideoStat(): VideoStatData? = withContext(Dispatchers.IO) {
        when (val resp = api.getVideoStat()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<VideoStatData>>() {}.type
                val apiResp: ApiResponse<VideoStatData>? = GsonConfig.gson.fromJson(resp.data, type)
                apiResp?.data
            }
            is Result.Error -> null
        }
    }

    suspend fun getBeUPTime(): ApiResult = withContext(Dispatchers.IO) {
        when (val resp = api.getBeUPTime()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<ScrollsData>>() {}.type
                val apiResp: ApiResponse<ScrollsData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                ApiResult(code = 0, message = "ok", business = data?.be_up?.be_up_minutes.toString())
            }
            is Result.Error -> ApiResult(code = resp.exception.code, message = resp.exception.message ?: "")
        }
    }
}
