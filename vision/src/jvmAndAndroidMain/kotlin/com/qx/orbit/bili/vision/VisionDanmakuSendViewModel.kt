package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliDanmakuGateway
import com.qx.orbit.bili.core.api.DanmakuGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionDanmakuSendState(
    val sending: Boolean = false,
    val sent: Boolean = false,
    val message: String? = null,
)

internal class VisionDanmakuSendViewModel(private val gateway: DanmakuGateway = BilibiliDanmakuGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionDanmakuSendState())
    val state = _state.asStateFlow()

    fun send(cid: Long, bvid: String, msg: String, progressMs: Long, color: Int, mode: Int, cookie: String, csrf: String) {
        if (_state.value.sending || _state.value.sent) return
        _state.value = VisionDanmakuSendState(sending = true)
        viewModelScope.launch {
            try {
                val ok = gateway.sendDanmaku(cid, bvid, msg, progressMs, color, mode, cookie, csrf)
                _state.value = VisionDanmakuSendState(sent = ok, message = if (ok) "弹幕已发送" else "发送失败，请重试")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _state.value = VisionDanmakuSendState(message = exception.message ?: "发送失败，请重试")
            }
        }
    }

    fun reset() {
        _state.value = VisionDanmakuSendState()
    }
}
