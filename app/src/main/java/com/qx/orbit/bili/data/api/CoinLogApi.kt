package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoinLogApi {

    private val api by lazy { BiliApiService.create() }

    internal data class CoinLogData(
        @SerializedName("list") val list: List<CoinLogItem>? = null
    )

    internal data class CoinLogItem(
        @SerializedName("time") val time: String? = null,
        @SerializedName("delta") val delta: Int = 0,
        @SerializedName("reason") val reason: String? = null
    )

    suspend fun getCoinLog(): List<CoinLog> = withContext(Dispatchers.IO) {
        when (val resp = api.getCoinLog()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<CoinLogData>>() {}.type
                val parsed: ApiResponse<CoinLogData>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data?.list?.map {
                    CoinLog(time = it.time ?: "", delta = it.delta, reason = it.reason ?: "")
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }
}
