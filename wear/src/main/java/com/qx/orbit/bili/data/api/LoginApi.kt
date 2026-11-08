package com.qx.orbit.bili.data.api

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.model.TvQrCodeAuth
import com.qx.orbit.bili.data.model.TvQrCodePoll
import com.qx.orbit.bili.data.remote.*
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import androidx.core.graphics.createBitmap

object LoginApi {

    private val api by lazy { BiliApiService.create() }

    internal data class QRGenerateData(
        @SerializedName("url") val url: String? = null,
        @SerializedName("qrcode_key") val qrcode_key: String? = null
    )

    data class QRLoginData(
        @SerializedName("url") val url: String? = null,
        @SerializedName("refresh_token") val refresh_token: String? = null,
        @SerializedName("timestamp") val timestamp: Long = 0,
        @SerializedName("code") val code: Int = 0,
        @SerializedName("message") val message: String? = null
    )

    suspend fun getLoginQR(): Pair<String, String> = withContext(Dispatchers.IO) {
        when (val resp = api.getLoginQr()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<QRGenerateData>>() {}.type
                val apiResp: ApiResponse<QRGenerateData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                Pair(data?.url ?: "", data?.qrcode_key ?: "")
            }
            is Result.Error -> Pair("", "")
        }
    }

    fun generateQRCodeBitmap(content: String, size: Int = 300): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "utf-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = createBitmap(size, size)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLoginState(qrcodeKey: String): QRLoginData = withContext(Dispatchers.IO) {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<QRLoginData>>() {}.type
        val resp: ApiResponse<QRLoginData>? = GsonConfig.gson.fromJson(json, type)
        resp?.data ?: QRLoginData()
    }

    suspend fun requestSSOs() = withContext(Dispatchers.IO) {
        val resp = api.requestSSOs()
        if (resp !is Result.Success) return@withContext
        val ssoType = object : TypeToken<ApiResponse<SsoData>>() {}.type
        val ssoResp: ApiResponse<SsoData>? = GsonConfig.gson.fromJson(resp.data, ssoType)
        val ssoData = ssoResp?.data ?: return@withContext
        for (sso in ssoData.data ?: return@withContext) {
            val ssoUrl = sso.url ?: continue
            val ssoRequest = Request.Builder().url(ssoUrl)
                .post(FormBody.Builder().build())
                .addHeader("Cookie", CookieManager.getCookie())
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()
            try {
                HttpClient.client.newCall(ssoRequest).execute().body?.string()
            } catch (_: Exception) {
            }
        }
    }

    internal data class SsoData(
        @SerializedName("data") val data: List<SsoItem>? = null
    )

    internal data class SsoItem(
        @SerializedName("url") val url: String? = null
    )

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"

    suspend fun getTvAuthCode(): Result<JsonElement> = withContext(Dispatchers.IO) {
        api.getTvAuthCode()
    }

    suspend fun pollTvQrCode(authCode: String): Result<JsonElement> = withContext(Dispatchers.IO) {
        api.pollTvQrCode(authCode)
    }
}
