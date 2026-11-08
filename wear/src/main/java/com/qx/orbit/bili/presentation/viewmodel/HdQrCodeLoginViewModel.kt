package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.qx.orbit.bili.data.api.BiliApiService
import com.qx.orbit.bili.data.api.LoginApi
import com.qx.orbit.bili.data.model.TvQrCodeAuth
import com.qx.orbit.bili.data.model.TvQrCodePoll
import com.qx.orbit.bili.data.remote.BilibiliApiException
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class HdQrCodeState(
    val qrCodeUrl: String? = null,
    val status: HdQrStatus = HdQrStatus.REQUESTING,
    val error: String? = null,
)

enum class HdQrStatus {
    REQUESTING, WAITING, SCANNED, LOGIN_SUCCESS, EXPIRED, ERROR
}

class HdQrCodeLoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HdQrCodeState())
    val uiState: StateFlow<HdQrCodeState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    private val biliApi by lazy { BiliApiService.create() }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun requestQrCode() {
        pollJob?.cancel()
        _uiState.value = HdQrCodeState(status = HdQrStatus.REQUESTING)

        viewModelScope.launch {
            try {
                when (val result = LoginApi.getTvAuthCode()) {
                    is Result.Success -> {
                        val data = GsonConfig.gson.fromJson(result.data.asJsonObject.get("data"), TvQrCodeAuth::class.java)
                        if (data == null || data.authCode.isBlank() || data.url.isBlank()) {
                            _uiState.value = HdQrCodeState(status = HdQrStatus.ERROR, error = "获取二维码失败")
                            return@launch
                        }
                        _uiState.value = HdQrCodeState(qrCodeUrl = data.url, status = HdQrStatus.WAITING)
                        startPolling(data.authCode)
                    }
                    is Result.Error -> {
                        _uiState.value = HdQrCodeState(status = HdQrStatus.ERROR, error = result.exception.message ?: "获取二维码失败")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = HdQrCodeState(status = HdQrStatus.ERROR, error = e.message ?: "获取二维码失败")
            }
        }
    }

    private fun startPolling(authCode: String) {
        pollJob = viewModelScope.launch {
            for (i in 0 until 180) {
                delay(1000.milliseconds)
                if (!isActive) break

                when (val result = LoginApi.pollTvQrCode(authCode)) {
                    is Result.Success -> {
                        val poll = GsonConfig.gson.fromJson(result.data.asJsonObject.get("data"), TvQrCodePoll::class.java)
                        if (poll != null && poll.tokenInfo != null) {
                            saveLoginResult(poll)
                            return@launch
                        }
                    }
                    is Result.Error -> {
                        when (result.exception.code) {
                            86038 -> {
                                _uiState.value = HdQrCodeState(status = HdQrStatus.EXPIRED, error = "二维码已过期")
                                return@launch
                            }
                            86090 -> {
                                _uiState.value = _uiState.value.copy(status = HdQrStatus.SCANNED)
                            }
                            86039, 86042 -> { /* 未扫码，继续轮询 */ }
                            else -> { /* 其他错误，继续轮询 */ }
                        }
                    }
                }
            }
            _uiState.value = HdQrCodeState(status = HdQrStatus.EXPIRED)
        }
    }

    private suspend fun saveLoginResult(poll: TvQrCodePoll) {
        val tokenInfo = poll.tokenInfo ?: return
        val cookies = poll.cookieInfo?.cookies.orEmpty()

        // 激活 cookie（空 payload,预留后续加密）
        try {
            val payload = com.google.gson.JsonObject().apply { addProperty("payload", "") }
            biliApi.activeCookie(payload)
        } catch (_: Exception) { /* 非必需,激活失败不影响 cookie 写入 */ }

        cookies.forEach { CookieManager.putCookie(it.name, it.value) }
        CookieManager.setMid(tokenInfo.mid)
        CookieManager.putCookie("access_token", tokenInfo.accessToken)
        CookieManager.putCookie("refresh_token", tokenInfo.refreshToken)

        _uiState.value = HdQrCodeState(status = HdQrStatus.LOGIN_SUCCESS)
    }
}