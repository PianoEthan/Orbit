package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliFeedGateway
import com.qx.orbit.bili.core.api.BilibiliSearchGateway
import com.qx.orbit.bili.core.api.SearchGateway
import com.qx.orbit.bili.core.api.CoreFeed
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.FeedGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisionHomeViewModel(
    private val gateway: FeedGateway = BilibiliFeedGateway(cookieProvider = { VisionUserSession.cookie }),
    private val searchGateway: SearchGateway = BilibiliSearchGateway(),
    private val loadOnStart: Boolean = true,
) : ViewModel() {
    private val _feed = MutableStateFlow(CoreFeed.RECOMMEND)
    val feed: StateFlow<CoreFeed> = _feed.asStateFlow()

    private val _videos = MutableStateFlow<List<CoreVideo>>(emptyList())
    val videos: StateFlow<List<CoreVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var page = 1
    private var generation = 0
    private var loadJob: Job? = null
    private var observedSession = VisionUserSession.cookie
    private val _feedRevision = MutableStateFlow(0)
    val feedRevision: StateFlow<Int> = _feedRevision.asStateFlow()

    fun onSessionChanged(sessionKey: String) {
        if (observedSession == sessionKey) return
        observedSession = sessionKey
        page = 1
        _videos.value = emptyList()
        _feedRevision.value++
        load(reset = true)
    }

    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    init {
        if (loadOnStart) load(reset = true)
    }

    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        _query.value = query
        page = 1
        _videos.value = emptyList()
        load(reset = true)
    }

    fun selectFeed(feed: CoreFeed) {
        if (_feed.value == feed && _query.value == null) return
        _query.value = null
        _feed.value = feed
        page = 1
        _videos.value = emptyList()
        load(reset = true)
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value) return
        load(reset = false)
    }

    fun refresh() {
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        val requestGeneration = ++generation
        loadJob?.cancel()
        val requestFeed = _feed.value
        val requestQuery = _query.value
        val requestPage = if (reset) 1 else page
        _isLoading.value = true
        _error.value = null
        if (reset) _hasMore.value = true
        loadJob = viewModelScope.launch {
            try {
                val loaded = if (requestQuery == null) gateway.loadFeed(requestFeed, requestPage)
                    else searchGateway.searchVideos(requestQuery, requestPage)
                if (requestGeneration != generation) return@launch
                val valid = loaded.filter { it.bvid.isNotBlank() }
                val previous = if (reset) emptyList() else _videos.value
                val combined = (previous + valid).distinctBy { it.bvid }
                _videos.value = combined
                _hasMore.value = valid.isNotEmpty() && (reset || combined.size > previous.size)
                page = requestPage + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (requestGeneration == generation) _error.value = exception.message ?: "加载失败"
            } finally {
                if (requestGeneration == generation) _isLoading.value = false
            }
        }
    }
}
