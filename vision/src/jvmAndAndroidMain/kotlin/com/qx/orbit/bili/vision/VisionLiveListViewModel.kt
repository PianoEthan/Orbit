package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliLiveGateway
import com.qx.orbit.bili.core.api.CoreLiveRoom
import com.qx.orbit.bili.core.api.LiveGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisionLiveFeed { RECOMMEND, FOLLOWED }

data class VisionLiveListState(
    val feed: VisionLiveFeed = VisionLiveFeed.RECOMMEND,
    val rooms: List<CoreLiveRoom> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
)

class VisionLiveListViewModel(
    private val gateway: LiveGateway = BilibiliLiveGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionLiveListState())
    val state: StateFlow<VisionLiveListState> = _state.asStateFlow()

    private var page = 1
    private var generation = 0
    private var job: Job? = null

    fun selectFeed(feed: VisionLiveFeed) {
        if (_state.value.feed == feed && _state.value.rooms.isNotEmpty()) return
        generation++
        job?.cancel()
        page = 1
        _state.value = _state.value.copy(feed = feed, rooms = emptyList(), error = null, hasMore = true)
        loadPage()
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        loadPage()
    }

    fun retry() {
        if (_state.value.loading) return
        loadPage()
    }

    private fun loadPage() {
        val feed = _state.value.feed
        val token = generation
        val requestedPage = page
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val rooms = when (feed) {
                    VisionLiveFeed.RECOMMEND -> gateway.loadRecommend(requestedPage)
                    VisionLiveFeed.FOLLOWED -> gateway.loadFollowed(requestedPage, VisionUserSession.cookie)
                }
                if (token != generation) return@launch
                val combined = (_state.value.rooms + rooms).distinctBy { it.roomId }
                _state.value = _state.value.copy(
                    rooms = combined,
                    loading = false,
                    hasMore = rooms.isNotEmpty(),
                )
                page = requestedPage + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == generation) {
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "直播列表加载失败")
                }
            }
        }
    }
}
