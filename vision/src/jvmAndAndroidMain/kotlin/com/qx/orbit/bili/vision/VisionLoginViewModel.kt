package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.AuthGateway
import com.qx.orbit.bili.core.api.BilibiliAuthGateway
import com.qx.orbit.bili.core.api.CoreQrCode
import com.qx.orbit.bili.core.api.CoreQrPollResult
import com.qx.orbit.bili.core.api.CoreQrPollStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionLoginState(
    val code: CoreQrCode? = null,
    val loading: Boolean = false,
    val status: CoreQrPollStatus = CoreQrPollStatus.WAITING_SCAN,
    val message: String = "正在获取登录二维码…",
    val result: CoreQrPollResult? = null,
)

internal class VisionLoginViewModel(
    private val gateway: AuthGateway = BilibiliAuthGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionLoginState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var active = false

    fun start() {
        active = true
        if (job?.isActive == true || _state.value.result != null) return
        if (_state.value.code == null) refresh() else poll(generation)
    }

    fun stop() {
        active = false
        generation++
        job?.cancel()
        job = null
    }

    fun refresh() {
        val request = ++generation
        job?.cancel()
        _state.value = VisionLoginState(loading = true)
        if (!active) return
        job = viewModelScope.launch {
            try {
                val code = gateway.generateQrCode()
                if (request != generation) return@launch
                _state.value = VisionLoginState(code = code, message = "请使用哔哩哔哩手机客户端扫一扫登录")
                pollLoop(request, code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == generation) _state.value = VisionLoginState(status = CoreQrPollStatus.ERROR, message = "无法获取二维码，请检查网络后刷新")
            }
        }
    }

    private fun poll(request: Int) {
        val code = _state.value.code ?: return
        if (_state.value.status == CoreQrPollStatus.EXPIRED) return
        job = viewModelScope.launch { pollLoop(request, code) }
    }

    private suspend fun pollLoop(request: Int, code: CoreQrCode) {
        var waitMs = 2000L
        while (active && request == generation) {
            delay(waitMs)
            try {
                val result = gateway.pollQrCode(code.qrcodeKey)
                if (request != generation) return
                if (result.status == CoreQrPollStatus.SUCCESS && !validVisionSession(result.cookies, result.mid)) {
                    _state.value = VisionLoginState(code = code, status = CoreQrPollStatus.EXPIRED, message = "登录凭证不完整，请刷新二维码重新扫码")
                    return
                }
                waitMs = if (result.status == CoreQrPollStatus.ERROR) (waitMs * 2).coerceAtMost(30_000) else 2000
                _state.value = VisionLoginState(code = code, status = result.status,
                    message = if (result.status == CoreQrPollStatus.ERROR) "登录暂不可用，${waitMs / 1000} 秒后重试；也可刷新二维码" else result.message,
                    result = result.takeIf { it.status == CoreQrPollStatus.SUCCESS })
                if (result.status == CoreQrPollStatus.SUCCESS || result.status == CoreQrPollStatus.EXPIRED) return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                waitMs = (waitMs * 2).coerceAtMost(30_000)
                if (request == generation) _state.value = VisionLoginState(code = code, status = CoreQrPollStatus.ERROR,
                    message = "网络异常，${waitMs / 1000} 秒后重试；也可刷新二维码")
            }
        }
    }
}
