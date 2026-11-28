package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliDynamicGateway
import com.qx.orbit.bili.core.api.CoreDynamic
import com.qx.orbit.bili.core.api.DynamicGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionDynamicDetailState(
    val dynamic: CoreDynamic? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val reposting: Boolean = false,
    val reposted: Boolean = false,
    val repostMessage: String? = null,
)

internal class VisionDynamicDetailViewModel(private val gateway: DynamicGateway = BilibiliDynamicGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionDynamicDetailState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun open(id: String, cookie: String) {
        job?.cancel()
        _state.value = VisionDynamicDetailState(loading = true)
        job = viewModelScope.launch {
            try {
                val dynamic = gateway.loadDetail(id, cookie)
                _state.value = VisionDynamicDetailState(dynamic = dynamic)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = VisionDynamicDetailState(error = "动态加载失败，请重试")
            }
        }
    }

    fun repost(id: String, text: String, cookie: String, csrf: String) {
        if (_state.value.reposting || _state.value.reposted) return
        _state.value = _state.value.copy(reposting = true, repostMessage = null)
        viewModelScope.launch {
            try {
                val ok = gateway.repost(id, text, cookie, csrf)
                _state.value = _state.value.copy(reposting = false, reposted = ok, repostMessage = if (ok) "转发成功" else "转发失败，请重试")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(reposting = false, repostMessage = "转发失败，请重试")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }
}
