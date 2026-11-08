package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliSearchGateway
import com.qx.orbit.bili.core.api.CoreHotword
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.SearchGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisionSearchViewModel(
    private val gateway: SearchGateway = BilibiliSearchGateway(),
    loadHotwordsOnStart: Boolean = true,
) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val _hotwords = MutableStateFlow<List<CoreHotword>>(emptyList())
    val hotwords: StateFlow<List<CoreHotword>> = _hotwords.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow<List<CoreVideo>>(emptyList())
    val results: StateFlow<List<CoreVideo>> = _results.asStateFlow()

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
        _results.value = emptyList()
        loadSearchPage(reset = true)
    }

    fun clearSearch() {
        searchGeneration++
        suggestionGeneration++
        searchJob?.cancel()
        suggestJob?.cancel()
        page = 1
        _query.value = null
        _results.value = emptyList()
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
            loadSearchPage(reset = _results.value.isEmpty())
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

    private fun loadSearchPage(reset: Boolean) {
        val activeQuery = _query.value ?: return
        val requestGeneration = ++searchGeneration
        searchJob?.cancel()
        val requestPage = if (reset) 1 else page
        _isLoading.value = true
        _error.value = null
        if (reset) _hasMore.value = true
        searchJob = viewModelScope.launch {
            try {
                val loaded = gateway.searchVideos(activeQuery, requestPage)
                if (requestGeneration != searchGeneration) return@launch
                val previous = if (reset) emptyList() else _results.value
                val combined = (previous + loaded).distinctBy { it.bvid }
                _results.value = combined
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
