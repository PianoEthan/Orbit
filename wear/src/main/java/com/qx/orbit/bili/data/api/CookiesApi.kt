package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.remote.*
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CookiesApi {

    private val api by lazy { BiliApiService.create() }

    internal data class BuvidData(
        @SerializedName("b_3") val b_3: String? = null
    )

    internal data class WebBuvidData(
        @SerializedName("b_3") val b_3: String? = null,
        @SerializedName("b_4") val b_4: String? = null
    )

    internal data class BiliTicketData(
        @SerializedName("ticket") val ticket: String? = null,
        @SerializedName("created_at") val created_at: Int = 0
    )

    suspend fun activeCookieInfo(): Int = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("payload", "{}") }
        when (val resp = api.activeCookie(payload)) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun getBuvid3Only(): String = withContext(Dispatchers.IO) {
        when (val resp = api.getBuvid3Only()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<BuvidData>>() {}.type
                val apiResp: ApiResponse<BuvidData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                data?.b_3 ?: ""
            }
            is Result.Error -> ""
        }
    }

    suspend fun getWebBuvids(): Pair<String, String> = withContext(Dispatchers.IO) {
        when (val resp = api.getWebBuvids()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<WebBuvidData>>() {}.type
                val apiResp: ApiResponse<WebBuvidData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                Pair(data?.b_3 ?: "", data?.b_4 ?: "")
            }
            is Result.Error -> Pair("", "")
        }
    }

    suspend fun genBiliTicket(): Pair<String, Int> = withContext(Dispatchers.IO) {
        val body = JsonObject().apply { addProperty("key_id", "ec02") }
        when (val resp = api.genBiliTicket(body)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<BiliTicketData>>() {}.type
                val apiResp: ApiResponse<BiliTicketData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                Pair(data?.ticket ?: "", data?.created_at ?: 0)
            }
            is Result.Error -> Pair("", 0)
        }
    }

    suspend fun checkCookies() = withContext(Dispatchers.IO) {
        activeCookieInfo()
        getBuvid3Only()
        val webBuvids = getWebBuvids()
        if (webBuvids.first.isNotEmpty()) {
            CookieManager.putCookie("buvid3", webBuvids.first)
        }
        if (webBuvids.second.isNotEmpty()) {
            CookieManager.putCookie("buvid4", webBuvids.second)
        }
        genBiliTicket()
    }
}
