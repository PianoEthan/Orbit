package com.qx.orbit.bili.core.api

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
