package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class CoreQrCode(val url: String, val qrcodeKey: String)

enum class CoreQrPollStatus {
    WAITING_SCAN,
    SCANNED_WAITING_CONFIRM,
    EXPIRED,
    SUCCESS,
    ERROR,
}

data class CoreQrPollResult(
    val status: CoreQrPollStatus,
    val message: String,
    val cookies: String = "",
    val mid: Long = 0L,
    val refreshToken: String = "",
)

data class CoreUserProfile(
    val mid: Long,
    val uname: String,
    val face: String,
    val coins: Double,
    val level: Int,
    val isLogin: Boolean,
)

interface AuthGateway {
    suspend fun generateQrCode(): CoreQrCode
    suspend fun pollQrCode(qrcodeKey: String): CoreQrPollResult
    suspend fun getMyProfile(cookie: String): CoreUserProfile
}

class BilibiliAuthGateway(private val client: OkHttpClient = OkHttpClient()) : AuthGateway {
    override suspend fun generateQrCode(): CoreQrCode {
        val json = client.getBilibiliJson("https://passport.bilibili.com/x/passport-login/web/qrcode/generate").requireBilibiliData()
        val url = json.string("url").orEmpty()
        val key = json.string("qrcode_key").orEmpty()
        check(url.startsWith("https://") && key.isNotBlank()) { "登录二维码数据无效，请重试" }
        return CoreQrCode(url, key)
    }

    override suspend fun pollQrCode(qrcodeKey: String): CoreQrPollResult {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey"
        val request = Request.Builder().url(url)
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BILIBILI_USER_AGENT)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(CoreQrPollResult(CoreQrPollStatus.ERROR, e.message ?: "网络请求失败"))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val cookiesList = response.headers("Set-Cookie").map { it.substringBefore(";") }
                        val cookies = cookiesList.joinToString("; ")
                        val text = response.use {
                            check(it.isSuccessful) { "登录请求失败：HTTP ${it.code}" }
                            it.body.string()
                        }
                        val root = JsonParser.parseString(text).asJsonObject
                        check(root.get("code")?.asInt == 0) { "登录接口返回错误" }
                        val data = root.getAsJsonObject("data") ?: error("登录响应缺少数据")
                        val code = data.get("code")?.asInt ?: error("登录响应缺少状态")
                        val message = data?.string("message") ?: root.string("message").orEmpty()
                        val refreshToken = data?.string("refresh_token").orEmpty()

                        val result = when (code) {
                            0 -> {
                                val mid = parseCookieValue(cookies, "DedeUserID").toLongOrNull() ?: 0L
                                check(mid > 0 && parseCookieValue(cookies, "SESSDATA").isNotBlank()) { "登录响应缺少有效凭证，请刷新二维码" }
                                CoreQrPollResult(CoreQrPollStatus.SUCCESS, "登录成功", cookies, mid, refreshToken)
                            }
                            86101 -> CoreQrPollResult(CoreQrPollStatus.WAITING_SCAN, "未扫码")
                            86090 -> CoreQrPollResult(CoreQrPollStatus.SCANNED_WAITING_CONFIRM, "已扫码，请在手机上确认")
                            86038 -> CoreQrPollResult(CoreQrPollStatus.EXPIRED, "二维码已失效")
                            else -> CoreQrPollResult(CoreQrPollStatus.ERROR, message.ifBlank { "登录异常 ($code)" })
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(CoreQrPollResult(CoreQrPollStatus.ERROR, e.message ?: "解析失败"))
                    }
                }
            })
        }
    }

    override suspend fun getMyProfile(cookie: String): CoreUserProfile {
        if (cookie.isBlank()) return unauthenticatedProfile()
        val json = client.getBilibiliJson("https://api.bilibili.com/x/web-interface/nav", cookie)
        val code = json.get("code")?.takeUnless { it.isJsonNull }?.asInt
        if (code == -101) return unauthenticatedProfile()
        check(code == 0) { "账号信息暂时不可用，请稍后重试" }
        val data = checkNotNull(json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject) { "账号信息不完整" }
        val isLogin = checkNotNull(data.get("isLogin")?.takeUnless { it.isJsonNull }?.asBoolean) { "登录状态缺失" }
        if (!isLogin) return unauthenticatedProfile()
        check((data.get("mid")?.takeUnless { it.isJsonNull }?.asLong ?: 0L) > 0) { "账号 UID 缺失" }

        val levelInfo = data.getAsJsonObject("level_info")
        return CoreUserProfile(
            mid = data.get("mid")?.asLong ?: 0L,
            uname = data.string("uname").orEmpty(),
            face = data.string("face").orEmpty().let {
                if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it
            },
            coins = data.get("money")?.asDouble ?: 0.0,
            level = levelInfo?.get("current_level")?.asInt ?: 0,
            isLogin = true,
        )
    }

    private fun unauthenticatedProfile() = CoreUserProfile(0L, "未登录", "", 0.0, 0, false)

    private fun JsonObject.string(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString
}

fun parseCookieValue(cookie: String, key: String): String {
    cookie.split(";").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.startsWith("$key=")) {
            return trimmed.substring(key.length + 1)
        }
    }
    return ""
}
