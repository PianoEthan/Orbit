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

internal data class VisionDynamicState(
    val items: List<CoreDynamic> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

internal class VisionDynamicViewModel(private val gateway: DynamicGateway = BilibiliDynamicGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionDynamicState())
    val state = _state.asStateFlow()
    private var cookie = ""
    private var offset: String? = null
    private var generation = 0
    private var job: Job? = null
    private var retryReset = true
    private var interrupted = false

    fun setAccount(session: String) {
        if (cookie == session) {
            if (interrupted && session.isNotBlank()) {
                interrupted = false
                retry()
            }
            return
        }
        interrupted = false
        cookie = session
        generation++
        job?.cancel()
        offset = null
        _state.value = VisionDynamicState()
        if (session.isNotBlank()) refresh()
    }

    fun refresh() { load(reset = true) }
    fun retry() { if (!_state.value.loading) load(reset = retryReset) }
    fun loadMore() { if (!_state.value.loading && _state.value.hasMore) load(reset = false) }

    fun stop() {
        interrupted = _state.value.loading
        generation++
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }

    private fun load(reset: Boolean) {
        if (cookie.isBlank()) return
        val requestedOffset = if (reset) "" else offset ?: return
        val request = ++generation
        job?.cancel()
        retryReset = reset
        val session = cookie
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val page = gateway.load(session, requestedOffset)
                if (request != generation) return@launch
                val previous = if (reset) emptyList() else _state.value.items
                val items = (previous + page.items).distinctBy { it.id }
                offset = page.nextOffset?.takeIf { it.isNotBlank() && it != requestedOffset }
                _state.value = VisionDynamicState(items, hasMore = offset != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) _state.value = _state.value.copy(loading = false, error = "动态加载失败，请重试")
            }
        }
    }
}
