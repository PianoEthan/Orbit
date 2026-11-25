package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliSearchGateway
import com.qx.orbit.bili.core.api.CoreHotword
import com.qx.orbit.bili.core.api.CoreLiveRoom
import com.qx.orbit.bili.core.api.CoreSearchSeason
import com.qx.orbit.bili.core.api.CoreSearchUser
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.SearchGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisionSearchTab(val title: String) {
    VIDEO("视频"), LIVE("直播"), USER("用户"), SEASON("番剧"),
}

class VisionSearchViewModel(
    private val gateway: SearchGateway = BilibiliSearchGateway(),
    loadHotwordsOnStart: Boolean = true,
) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val _tab = MutableStateFlow(VisionSearchTab.VIDEO)
    val tab: StateFlow<VisionSearchTab> = _tab.asStateFlow()

    private val _hotwords = MutableStateFlow<List<CoreHotword>>(emptyList())
    val hotwords: StateFlow<List<CoreHotword>> = _hotwords.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow<List<CoreVideo>>(emptyList())
    val results: StateFlow<List<CoreVideo>> = _results.asStateFlow()

    private val _roomResults = MutableStateFlow<List<CoreLiveRoom>>(emptyList())
    val roomResults: StateFlow<List<CoreLiveRoom>> = _roomResults.asStateFlow()

    private val _userResults = MutableStateFlow<List<CoreSearchUser>>(emptyList())
    val userResults: StateFlow<List<CoreSearchUser>> = _userResults.asStateFlow()

    private val _seasonResults = MutableStateFlow<List<CoreSearchSeason>>(emptyList())
    val seasonResults: StateFlow<List<CoreSearchSeason>> = _seasonResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var searchGeneration = 0
    private var suggestionGeneration = 0
    private var hotwordGeneration = 0
    private var hotwordJob: Job? = null

    init {
        if (loadHotwordsOnStart) loadHotwords()
    }

    fun switchTab(tab: VisionSearchTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        clearTabResults()
        if (_query.value != null) loadSearchPage(reset = true)
    }

    fun onTextChanged(newText: String) {
        _text.value = newText
        suggestJob?.cancel()
        val request = ++suggestionGeneration
        _suggestions.value = emptyList()
        val keyword = newText.trim()
        if (keyword.isEmpty()) return
        suggestJob = viewModelScope.launch {
            delay(250)
            try {
                val suggestions = gateway.loadSuggestions(keyword)
                if (request == suggestionGeneration && _text.value.trim() == keyword) _suggestions.value = suggestions
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == suggestionGeneration) _suggestions.value = emptyList()
            }
        }
    }

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        suggestJob?.cancel()
        suggestionGeneration++
        if (_query.value == trimmed && _isLoading.value) return
        _text.value = trimmed
        _query.value = trimmed
        _suggestions.value = emptyList()
        page = 1
        clearTabResults()
        loadSearchPage(reset = true)
    }

    fun clearSearch() {
        searchGeneration++
        suggestionGeneration++
        searchJob?.cancel()
        suggestJob?.cancel()
        page = 1
        _query.value = null
        clearTabResults()
        _error.value = null
        _isLoading.value = false
        _hasMore.value = true
        _text.value = ""
        _suggestions.value = emptyList()
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMore.value || _query.value == null) return
        loadSearchPage(reset = false)
    }

    fun retry() {
        if (_isLoading.value) return
        if (_query.value != null) {
            loadSearchPage(reset = currentResultCount() == 0)
        } else {
            loadHotwords()
        }
    }

    fun loadHotwords() {
        val request = ++hotwordGeneration
        hotwordJob?.cancel()
        hotwordJob = viewModelScope.launch {
            try {
                val words = gateway.loadHotwords()
                if (request == hotwordGeneration) _hotwords.value = words
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == hotwordGeneration && _hotwords.value.isEmpty()) _hotwords.value = emptyList()
            }
        }
    }

    private fun currentResultCount() = when (_tab.value) {
        VisionSearchTab.VIDEO -> _results.value.size
        VisionSearchTab.LIVE -> _roomResults.value.size
        VisionSearchTab.USER -> _userResults.value.size
        VisionSearchTab.SEASON -> _seasonResults.value.size
    }

    private fun clearTabResults() {
        _results.value = emptyList()
        _roomResults.value = emptyList()
        _userResults.value = emptyList()
        _seasonResults.value = emptyList()
    }

    private fun loadSearchPage(reset: Boolean) {
        val activeQuery = _query.value ?: return
        val activeTab = _tab.value
        val requestGeneration = ++searchGeneration
        searchJob?.cancel()
        val requestPage = if (reset) 1 else page
        _isLoading.value = true
        _error.value = null
        if (reset) _hasMore.value = true
        searchJob = viewModelScope.launch {
            try {
                val previous: List<Any> = when (activeTab) {
                    VisionSearchTab.VIDEO -> _results.value
                    VisionSearchTab.LIVE -> _roomResults.value
                    VisionSearchTab.USER -> _userResults.value
                    VisionSearchTab.SEASON -> _seasonResults.value
                }
                val base = if (reset) emptyList<Any>() else previous
                val loaded: List<Any> = when (activeTab) {
                    VisionSearchTab.VIDEO -> gateway.searchVideos(activeQuery, requestPage)
                    VisionSearchTab.LIVE -> gateway.searchRooms(activeQuery, requestPage)
                    VisionSearchTab.USER -> gateway.searchUsers(activeQuery, requestPage)
                    VisionSearchTab.SEASON -> gateway.searchSeasons(activeQuery, requestPage)
                }
                val combined: List<Any> = when (activeTab) {
                    VisionSearchTab.VIDEO -> (base.filterIsInstance<CoreVideo>() + loaded).distinctBy { (it as CoreVideo).bvid }
                    VisionSearchTab.LIVE -> (base.filterIsInstance<CoreLiveRoom>() + loaded).distinctBy { (it as CoreLiveRoom).roomId }
                    VisionSearchTab.USER -> (base.filterIsInstance<CoreSearchUser>() + loaded).distinctBy { (it as CoreSearchUser).mid }
                    VisionSearchTab.SEASON -> (base.filterIsInstance<CoreSearchSeason>() + loaded).distinctBy { (it as CoreSearchSeason).seasonId }
                }
                if (requestGeneration != searchGeneration) return@launch
                when (activeTab) {
                    VisionSearchTab.VIDEO -> _results.value = combined.filterIsInstance<CoreVideo>()
                    VisionSearchTab.LIVE -> _roomResults.value = combined.filterIsInstance<CoreLiveRoom>()
                    VisionSearchTab.USER -> _userResults.value = combined.filterIsInstance<CoreSearchUser>()
                    VisionSearchTab.SEASON -> _seasonResults.value = combined.filterIsInstance<CoreSearchSeason>()
                }
                _hasMore.value = loaded.isNotEmpty() && (reset || combined.size > previous.size)
                page = requestPage + 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (requestGeneration == searchGeneration) {
                    _error.value = exception.message ?: "搜索失败，请重试"
                }
            } finally {
                if (requestGeneration == searchGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }
}
