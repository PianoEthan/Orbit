package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExpLogApi {

    private val api by lazy { BiliApiService.create() }

    internal data class ExpLogData(
        @SerializedName("list") val list: List<ExpLogItem>? = null
    )

    internal data class ExpLogItem(
        @SerializedName("delta") val delta: Int = 0,
        @SerializedName("time") val time: String? = null,
        @SerializedName("reason") val reason: String? = null
    )

    suspend fun getExpLog(): List<ExpLog> = withContext(Dispatchers.IO) {
        when (val resp = api.getExpLog()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<ExpLogData>>() {}.type
                val apiResp: ApiResponse<ExpLogData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                data?.list?.map {
                    ExpLog(delta = it.delta, time = it.time ?: "", reason = it.reason ?: "")
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }
}
