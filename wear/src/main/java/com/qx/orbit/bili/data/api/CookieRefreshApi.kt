package com.qx.orbit.bili.data.api

import android.util.Base64
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.qx.orbit.bili.data.api.BiliApiService
import com.qx.orbit.bili.data.model.ApiResponse
import com.qx.orbit.bili.data.model.TvRefreshData
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object CookieRefreshApi {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"

    internal data class CookieInfoData(
        @SerializedName("refresh") val refresh: Boolean = false,
        @SerializedName("timestamp") val timestamp: Long = 0
    )

    internal data class CookieRefreshData(
        @SerializedName("status") val status: Int = 0,
        @SerializedName("message") val message: String = "",
        @SerializedName("refresh_token") val refresh_token: String = ""
    )

    data class RefreshResult(
        val isSuccess: Boolean,
        val message: String,
        val isNeedRefresh: Boolean = false
    )

    suspend fun doCookieRefresh(auto: Boolean = false): RefreshResult = withContext(Dispatchers.IO) {
        try {
            if (CookieManager.getCookie().isEmpty()) {
                return@withContext RefreshResult(false, "未登录")
            }
            if (CookieManager.getCookie().contains("access_token=")) {
                return@withContext doTvCookieRefresh()
            }

            // 1. 检查是否需要刷新
            val infoUrl = "https://passport.bilibili.com/x/passport-login/web/cookie/info?csrf=${CookieManager.getCsrf()}"
            val infoJson = httpGet(infoUrl)
            val infoType = object : TypeToken<ApiResponse<CookieInfoData>>() {}.type
            val infoResp: ApiResponse<CookieInfoData>? = GsonConfig.gson.fromJson(infoJson, infoType)
            
            if (infoResp == null || infoResp.code != 0) {
                return@withContext RefreshResult(false, "API错误 ${infoResp?.code}: ${infoResp?.message ?: "检查刷新状态失败"}")
            }

            val timestamp = infoResp.data?.timestamp ?: System.currentTimeMillis()
            val needRefresh = infoResp.data?.refresh == true

            if (auto && !needRefresh) {
                return@withContext RefreshResult(true, "无需刷新", false)
            }

            val oldRefreshToken = CookieManager.getRefreshToken()
            if (oldRefreshToken.isEmpty()) {
                return@withContext RefreshResult(false, "缺少持久化刷新口令 (refresh_token)")
            }

            // 2. 生成 CorrespondPath
            val correspondPath = getCorrespondPath(timestamp)

            // 3. 获取 refresh_csrf
            val htmlUrl = "https://www.bilibili.com/correspond/1/$correspondPath"
            val htmlContent = httpGet(htmlUrl)
            val refreshCsrf = extractRefreshCsrf(htmlContent)
            if (refreshCsrf.isEmpty()) {
                return@withContext RefreshResult(false, "获取 refresh_csrf 失败，可能 Path 错误或过期")
            }

            // 4. 执行刷新 Cookie
            val refreshUrl = "https://passport.bilibili.com/x/passport-login/web/cookie/refresh"
            val refreshBody = FormBody.Builder()
                .add("csrf", CookieManager.getCsrf())
                .add("refresh_csrf", refreshCsrf)
                .add("source", "main_web")
                .add("refresh_token", oldRefreshToken)
                .build()
            
            // 需要手动解析 response，因为刷新成功时会下发 set-cookie，HttpClient 会拦截并保存它们！
            val refreshRequest = Request.Builder().url(refreshUrl)
                .post(refreshBody)
                .build()
            val refreshResponse = HttpClient.client.newCall(refreshRequest).execute()
            val refreshJson = refreshResponse.body?.string() ?: ""
            val refreshType = object : TypeToken<ApiResponse<CookieRefreshData>>() {}.type
            val refreshResp: ApiResponse<CookieRefreshData>? = GsonConfig.gson.fromJson(refreshJson, refreshType)

            if (refreshResp == null || refreshResp.code != 0) {
                return@withContext RefreshResult(false, "API错误 ${refreshResp?.code}: ${refreshResp?.message ?: "刷新请求失败"}")
            }

            val newRefreshToken = refreshResp.data?.refresh_token ?: ""
            if (newRefreshToken.isNotEmpty()) {
                // 此时 SESSDATA 等新的 Cookie 应该已经被 HttpClient 的拦截器保存
                // 我们还需要保存新的 refresh_token
                CookieManager.putCookie("refresh_token", newRefreshToken)
            }

            // 5. 确认更新 (使得旧的 refresh_token 失效)
            // 注意：此时 CookieManager.getCookie() 和 getCsrf() 返回的是新的一组
            val confirmUrl = "https://passport.bilibili.com/x/passport-login/web/confirm/refresh"
            val confirmBody = FormBody.Builder()
                .add("csrf", CookieManager.getCsrf())
                .add("refresh_token", oldRefreshToken)
                .build()
            
            val confirmRequest = Request.Builder().url(confirmUrl)
                .post(confirmBody)
                .build()
            val confirmResponse = HttpClient.client.newCall(confirmRequest).execute()
            val confirmJson = confirmResponse.body?.string() ?: ""
            val confirmType = object : TypeToken<ApiResponse<Any>>() {}.type
            val confirmResp: ApiResponse<Any>? = GsonConfig.gson.fromJson(confirmJson, confirmType)
            
            if (confirmResp == null || confirmResp.code != 0) {
                // 虽然确认失败了，但是 Cookie 已经更新成功，所以整体算成功，只是旧口令未失效
                return@withContext RefreshResult(true, "刷新成功，但确认失败：${confirmResp?.message}", true)
            }

            return@withContext RefreshResult(true, "刷新成功", true)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RefreshResult(false, "异常：${e.message}")
        }
    }

    private suspend fun doTvCookieRefresh(): RefreshResult = withContext(Dispatchers.IO) {
        try {
            val accessToken = CookieManager.getInfoFromCookie("access_token")
            val refreshToken = CookieManager.getInfoFromCookie("refresh_token")
            if (accessToken.isBlank() || refreshToken.isBlank()) {
                return@withContext RefreshResult(false, "缺少 TV Token 信息")
            }
            
            val api = BiliApiService.create()
            val result = api.refreshTvToken(accessToken, refreshToken)
            if (result !is Result.Success) {
                return@withContext RefreshResult(false, "网络错误或刷新失败")
            }
            
            val type = object : TypeToken<ApiResponse<TvRefreshData>>() {}.type
            val refreshResp: ApiResponse<TvRefreshData>? = GsonConfig.gson.fromJson(result.data, type)
            
            if (refreshResp == null || refreshResp.code != 0) {
                return@withContext RefreshResult(false, "API错误 ${refreshResp?.code}: ${refreshResp?.message ?: "TV刷新失败"}")
            }
            
            val tokenInfo = refreshResp.data?.tokenInfo
            val cookieInfo = refreshResp.data?.cookieInfo
            if (tokenInfo != null) {
                CookieManager.putCookie("access_token", tokenInfo.accessToken)
                CookieManager.putCookie("refresh_token", tokenInfo.refreshToken)
            }
            
            cookieInfo?.cookies?.forEach {
                CookieManager.putCookie(it.name, it.value)
            }
            
            // 重新激活 cookie (TV/HD 特有)
            try {
                val payload = com.google.gson.JsonObject().apply { addProperty("payload", "") }
                api.activeCookie(payload)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            return@withContext RefreshResult(true, "TV Cookie 刷新成功", true)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RefreshResult(false, "异常：${e.message}")
        }
    }

    private fun getCorrespondPath(timestamp: Long): String {
        val publicKeyPEM = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0Eg
            Uc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71
            nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40
            JNrRuoEUXpabUzGB8QIDAQAB
            -----END PUBLIC KEY-----
        """.trimIndent()

        val publicKeyBytes = Base64.decode(
            publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .trim(),
            Base64.DEFAULT
        )

        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
            )
        }

        val encrypted = cipher.doFinal("refresh_$timestamp".toByteArray())
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    private fun extractRefreshCsrf(html: String): String {
        val regex = "<div id=\"1-name\">([^<]+)</div>".toRegex()
        val matchResult = regex.find(html)
        return matchResult?.groupValues?.get(1) ?: ""
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }
}
