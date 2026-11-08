package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName

/** TV/HD 扫码登录 - 获取 auth_code 响应 */
data class TvQrCodeAuth(
    @SerializedName("auth_code") val authCode: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0,
)

/** TV/HD 扫码登录 - 轮询结果 */
data class TvQrCodePoll(
    @SerializedName("auth_code") val authCode: String = "",
    @SerializedName("status") val status: Boolean = false,
    @SerializedName("token_info") val tokenInfo: TvTokenInfo? = null,
    @SerializedName("cookie_info") val cookieInfo: TvCookieInfo? = null,
)

data class TvTokenInfo(
    @SerializedName("mid") val mid: Long = 0,
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0,
)

data class TvCookieInfo(
    @SerializedName("cookies") val cookies: List<TvCookie> = emptyList(),
)

data class TvCookie(
    @SerializedName("name") val name: String,
    @SerializedName("value") val value: String,
    @SerializedName("http_only") val httpOnly: Int = 0,
    @SerializedName("expires") val expires: Long = 0,
)

data class TvRefreshData(
    @SerializedName("token_info") val tokenInfo: TvTokenInfo? = null,
    @SerializedName("cookie_info") val cookieInfo: TvCookieInfo? = null,
)
