package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliCreatorGateway
import com.qx.orbit.bili.core.api.CoreCreator
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CreatorGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisionCreatorState(
    val creator: CoreCreator? = null,
    val videos: List<CoreVideo> = emptyList(),
    val loading: Boolean = false,
    val videosLoading: Boolean = false,
    val error: String? = null,
    val videosError: String? = null,
    val hasMore: Boolean = false,
    val pending: Boolean = false,
    val message: String? = null,
)

class VisionCreatorViewModel(private val gateway: CreatorGateway = BilibiliCreatorGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionCreatorState())
    val state = _state.asStateFlow()
    private var mid = 0L
    private var cookie = ""
    private var generation = 0
    private var page = 1
    private var profileJob: Job? = null
    private var videosJob: Job? = null

    fun open(mid: Long, cookie: String) {
        if (mid == this.mid && cookie == this.cookie) return
        generation++
        profileJob?.cancel()
        videosJob?.cancel()
        this.mid = mid
        this.cookie = cookie
        page = 1
        _state.value = VisionCreatorState()
        refreshProfile()
        loadVideos()
    }

    fun refreshProfile() {
        if (mid <= 0 || _state.value.pending) return
        profileJob?.cancel()
        val token = generation
        _state.value = _state.value.copy(loading = true, error = null)
        profileJob = viewModelScope.launch {
            try {
                val creator = gateway.loadCreator(mid, cookie)
                if (token == generation) _state.value = _state.value.copy(creator = creator, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "资料加载失败")
            }
        }
    }

    fun loadVideos() {
        if (mid <= 0 || _state.value.videosLoading || (page > 1 && !_state.value.hasMore)) return
        val token = generation
        val requestedPage = page
        _state.value = _state.value.copy(videosLoading = true, videosError = null)
        videosJob = viewModelScope.launch {
            try {
                val result = gateway.loadVideos(mid, requestedPage, cookie)
                if (token == generation) {
                    _state.value = _state.value.copy(videos = (_state.value.videos + result.videos).distinctBy { it.bvid },
                        videosLoading = false, hasMore = result.hasMore)
                    page = requestedPage + 1
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(videosLoading = false, videosError = e.message ?: "投稿加载失败")
            }
        }
    }

    fun changeRelation(action: Int, csrf: String) {
        if (_state.value.pending || _state.value.loading || _state.value.creator == null || cookie.isBlank() || csrf.isBlank()) return
        val token = generation
        val targetMid = mid
        val account = cookie
        _state.value = _state.value.copy(pending = true, message = null)
        viewModelScope.launch {
            try {
                gateway.changeRelation(targetMid, action, account, csrf)
                if (token == generation) {
                    _state.value = _state.value.copy(pending = false, message = "操作已完成")
                    refreshProfile()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (token == generation) {
                    _state.value = _state.value.copy(pending = false, message = "操作结果未确认，请刷新资料后再操作")
                    refreshProfile()
                }
            }
        }
    }
}
