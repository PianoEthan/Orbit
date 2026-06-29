package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.remote.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CookieRefreshApi {

    private val api by lazy { BiliApiService.create() }

    data class CookieInfoData(
        @SerializedName("refresh") val refresh: Boolean = false,
        @SerializedName("timestamp") val timestamp: Long = 0
    )

    suspend fun cookieInfo(): CookieInfoData? = withContext(Dispatchers.IO) {
        when (val resp = api.cookieInfo()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<CookieInfoData>>() {}.type
                val apiResp: ApiResponse<CookieInfoData>? = GsonConfig.gson.fromJson(resp.data, type)
                apiResp?.data
            }
            is Result.Error -> null
        }
    }

    suspend fun refreshCookie(csrf: String): Boolean = withContext(Dispatchers.IO) {
        when (val resp = api.refreshCookie(csrf)) {
            is Result.Success -> {
                api.confirmRefreshCookie(CookieManager.getCsrf())
                true
            }
            is Result.Error -> false
        }
    }
}
