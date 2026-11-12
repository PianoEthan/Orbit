package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliRelatedGateway
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.RelatedGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisionRelatedState(val videos: List<CoreVideo> = emptyList(), val loading: Boolean = false, val error: String? = null)

class VisionRelatedViewModel(private val gateway: RelatedGateway = BilibiliRelatedGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionRelatedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun load(bvid: String, cookie: String) {
        val token = ++generation
        job?.cancel()
        _state.value = VisionRelatedState(loading = true)
        job = viewModelScope.launch {
            try {
                val videos = gateway.loadRelated(bvid, cookie)
                if (token == generation) _state.value = VisionRelatedState(videos)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = VisionRelatedState(error = e.message ?: "相关推荐加载失败")
            }
        }
    }
}
