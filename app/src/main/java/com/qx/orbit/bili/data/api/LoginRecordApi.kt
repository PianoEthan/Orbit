package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LoginRecordApi {

    private val api by lazy { BiliApiService.create() }

    internal data class LoginRecordData(
        @SerializedName("list") val list: List<LoginRecordItem>? = null
    )

    internal data class LoginRecordItem(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("device") val device: String? = null,
        @SerializedName("login_type") val login_type: String? = null,
        @SerializedName("time") val time: String? = null,
        @SerializedName("location") val location: String? = null,
        @SerializedName("ip") val ip: String? = null
    )

    suspend fun getLoginRecord(mid: Long, buvid: String): List<LoginRecord> = withContext(Dispatchers.IO) {
        when (val resp = api.getLoginRecord(mid, buvid)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<LoginRecordData>>() {}.type
                val apiResp: ApiResponse<LoginRecordData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                data?.list?.map {
                    LoginRecord(
                        mid = it.mid,
                        deviceName = it.device ?: "",
                        loginType = it.login_type ?: "",
                        loginTime = it.time ?: "",
                        location = it.location ?: "",
                        ip = it.ip ?: ""
                    )
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }
}
