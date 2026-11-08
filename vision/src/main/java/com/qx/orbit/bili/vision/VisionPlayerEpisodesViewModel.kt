package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliVideoGateway
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoPage
import com.qx.orbit.bili.core.api.VideoGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionPlayerEpisodesState(
    val pages: List<CoreVideoPage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

internal class VisionPlayerEpisodesViewModel(private val gateway: VideoGateway = BilibiliVideoGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionPlayerEpisodesState())
    val state = _state.asStateFlow()
    private var video: CoreVideo? = null
    private var generation = 0
    private var job: Job? = null

    fun open(target: CoreVideo, force: Boolean = false) {
        if (!force && video?.bvid == target.bvid && (_state.value.loading || _state.value.pages.isNotEmpty())) return
        val same = video?.bvid == target.bvid
        video = target
        val request = ++generation
        job?.cancel()
        _state.value = VisionPlayerEpisodesState(if (same) _state.value.pages else emptyList(), loading = true)
        job = viewModelScope.launch {
            try {
                val result = gateway.loadDetails(target)
                if (request == generation) _state.value = VisionPlayerEpisodesState(result.pages.filter { it.cid > 0 }.distinctBy { it.cid })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) _state.value = _state.value.copy(loading = false, error = "选集加载失败，当前播放不受影响")
            }
        }
    }

    fun retry() { video?.let { open(it, force = true) } }
}
