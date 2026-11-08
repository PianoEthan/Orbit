package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliUserDataGateway
import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.UserDataGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionFavoritePickerState(
    val folders: List<CoreFavFolder> = emptyList(),
    val selected: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal class VisionFavoritePickerViewModel(private val gateway: UserDataGateway = BilibiliUserDataGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionFavoritePickerState())
    val state = _state.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun load(cookie: String, mid: Long) {
        val request = ++generation
        job?.cancel()
        _state.value = VisionFavoritePickerState(loading = true)
        if (cookie.isBlank() || mid <= 0) {
            _state.value = VisionFavoritePickerState(error = "请先登录")
            return
        }
        job = viewModelScope.launch {
            try {
                val folders = gateway.loadFavFolders(cookie, mid)
                if (request == generation) _state.value = VisionFavoritePickerState(folders.distinctBy { it.id })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) _state.value = VisionFavoritePickerState(error = "收藏夹加载失败，请重试")
            }
        }
    }

    fun select(id: Long) {
        if (_state.value.folders.any { it.id == id }) _state.value = _state.value.copy(selected = id)
    }

    fun stop() {
        generation++
        job?.cancel()
        _state.value = VisionFavoritePickerState()
    }
}
