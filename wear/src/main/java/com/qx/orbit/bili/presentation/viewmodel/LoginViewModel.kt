package com.qx.orbit.bili.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.LoginApi
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed class LoginState {
    object Initial : LoginState()
    object LoadingQRCode : LoginState()
    data class QRCodeReady(val qrImage: Bitmap?, val message: String = "请使用哔哩哔哩手机客户端扫描二维码") : LoginState()
    object Success : LoginState()
    data class Error(val error: String) : LoginState()
}

class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Initial)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun startLogin() {
        _state.value = LoginState.LoadingQRCode
        pollJob?.cancel()
        
        viewModelScope.launch {
            try {
                val pair = LoginApi.getLoginQR()
                val url = pair.first
                val qrcodeKey = pair.second
                
                if (url.isEmpty() || qrcodeKey.isEmpty()) {
                    _state.value = LoginState.Error("获取登录二维码失败")
                    return@launch
                }
                
                val qrImage = LoginApi.generateQRCodeBitmap(url, 300)
                _state.value = LoginState.QRCodeReady(qrImage)
                
                startPolling(qrcodeKey)
            } catch (e: Exception) {
                _state.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }

    private fun startPolling(qrcodeKey: String) {
        pollJob = viewModelScope.launch {
            while (true) {
                delay(1000.milliseconds)
                try {
                    val pollData = LoginApi.getLoginState(qrcodeKey)
                    when (pollData.code) {
                        0 -> {
                            pollData.refresh_token?.let {
                                if (it.isNotEmpty()) {
                                    CookieManager.putCookie("refresh_token", it)
                                }
                            }
                            _state.value = LoginState.Success
                            break
                        }
                        86090 -> {
                            // Scanned, waiting for confirmation
                            if (_state.value is LoginState.QRCodeReady) {
                                val current = _state.value as LoginState.QRCodeReady
                                _state.value = current.copy(message = "已扫码，请在手机上继续操作")
                            }
                        }
                        86101 -> {
                            // Waiting to be scanned
                            if (_state.value is LoginState.QRCodeReady) {
                                val current = _state.value as LoginState.QRCodeReady
                                _state.value = current.copy(message = "请使用哔哩哔哩手机客户端扫描二维码")
                            }
                        }
                        86038 -> {
                            // Expired
                            _state.value = LoginState.Error("二维码已失效，请重试")
                            break
                        }
                        else -> {
                            // Keep polling
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
