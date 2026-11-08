package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliVideoGateway
import com.qx.orbit.bili.core.api.CoreVideoReply
import com.qx.orbit.bili.core.api.VideoGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionReplyThreadState(
    val root: CoreVideoReply? = null,
    val replies: List<CoreVideoReply> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

internal class VisionReplyThreadViewModel(private val gateway: VideoGateway = BilibiliVideoGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionReplyThreadState())
    val state = _state.asStateFlow()
    private var aid = 0L
    private var root = 0L
    private var cookie = ""
    private var nextPage = 1
    private var generation = 0
    private var job: Job? = null

    fun open(videoId: Long, rootId: Long, session: String) {
        if (aid == videoId && root == rootId && cookie == session && (_state.value.loading || _state.value.root != null)) return
        aid = videoId
        root = rootId
        cookie = session
        generation++
        job?.cancel()
        nextPage = 1
        _state.value = VisionReplyThreadState()
        load()
    }

    fun refresh() {
        generation++
        job?.cancel()
        nextPage = 1
        load()
    }

    fun stop() {
        generation++
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        load()
    }

    fun retry() {
        if (!_state.value.loading) load()
    }

    private fun load() {
        if (aid <= 0 || root <= 0) return
        val request = ++generation
        val page = nextPage
        val videoId = aid
        val rootId = root
        val session = cookie
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val result = gateway.loadReplyThread(videoId, rootId, page, session)
                if (request != generation) return@launch
                val previous = if (page == 1) emptyList() else _state.value.replies
                val replies = (previous + result.replies).filterNot { it.rpid == rootId }.distinctBy { it.rpid }
                _state.value = VisionReplyThreadState(result.root ?: _state.value.root, replies,
                    hasMore = result.hasMore && (page == 1 || replies.size > previous.size))
                nextPage = page + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == generation) _state.value = _state.value.copy(loading = false, error = "回复加载失败，请重试；部分评论可能需要登录")
            }
        }
    }
}
