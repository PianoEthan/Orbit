package com.qx.orbit.bili.data.api

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.model.TvQrCodeAuth
import com.qx.orbit.bili.data.model.TvQrCodePoll
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.sign.AppSignUtil
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** LoginApi —— Web / HD 扫码登录端点 + Cookie 导入。 */
object LoginApi {

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
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<QRGenerateData>>() {}.type
        val resp: ApiResponse<QRGenerateData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair("", "")
        Pair(resp.data.url ?: "", resp.data.qrcode_key ?: "")
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
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
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
        val url = "https://passport.bilibili.com/x/passport-login/web/sso/list"
        val body = FormBody.Builder().build()
        val request = Request.Builder().url(url)
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val response = HttpClient.client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return@withContext
        try {
            val json = JSONObject(responseBody)
            val dataArray = json.optJSONObject("data")?.optJSONArray("data") ?: return@withContext
            for (i in 0 until dataArray.length()) {
                val ssoUrl = dataArray.optJSONObject(i)?.optString("url") ?: continue
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
        } catch (_: Exception) {
        }
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"

    // ===== HD / TV 扫码登录(KiliKili 迁入)=====

    /**
     * HD 扫码登录：获取 TV 端 auth_code。
     * 服务端字段约定：mobi_app=android_hd, platform=android。
     */
    suspend fun getTvAuthCode(): TvQrCodeAuth? = withContext(Dispatchers.IO) {
        val signed = AppSignUtil.sign(
            mutableMapOf(
                "local_id" to "0",
                "mobi_app" to "android_hd",
                "platform" to "android",
            )
        )
        val body = signed.toFormBody()
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", AppSignUtil.HD_USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .addHeader("Origin", "https://www.bilibili.com")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val type = object : TypeToken<ApiResponse<TvQrCodeAuth>>() {}.type
        val resp: ApiResponse<TvQrCodeAuth>? = GsonConfig.gson.fromJson(json, type)
        if (resp?.isSuccess == true) resp.data else null
    }

    /**
     * HD 扫码登录：轮询扫码结果。
     *
     * 服务端错误码：
     *  - 86038：二维码已过期
     *  - 86039 / 86042：未扫码（继续轮询）
     *  - 86090：已扫码未确认（继续轮询）
     */
    suspend fun pollTvQrCode(authCode: String): TvQrCodePoll? = withContext(Dispatchers.IO) {
        val signed = AppSignUtil.sign(
            mutableMapOf(
                "auth_code" to authCode,
                "local_id" to "0",
                "mobi_app" to "android_hd",
                "platform" to "android",
            )
        )
        val body = signed.toFormBody()
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", AppSignUtil.HD_USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .addHeader("Origin", "https://www.bilibili.com")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val type = object : TypeToken<ApiResponse<TvQrCodePoll>>() {}.type
        val resp: ApiResponse<TvQrCodePoll>? = GsonConfig.gson.fromJson(json, type)
        resp?.data
    }

    private fun Map<String, String>.toFormBody(): okhttp3.FormBody {
        val builder = FormBody.Builder()
        forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }
}
