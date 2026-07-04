package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.SearchApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchTab(val title: String, val type: String) {
    VIDEO("视频", "video"),
    LIVE("直播", "live"),
    USER("用户", "bili_user"),
    ARTICLE("图文", "article"),
    BANGUMI("番剧", "media_bangumi")
}

class SearchViewModel : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentTab = MutableStateFlow(SearchTab.VIDEO)
    val currentTab: StateFlow<SearchTab> = _currentTab.asStateFlow()

    private val _results = MutableStateFlow<Map<SearchTab, List<Any>>>(emptyMap())
    val results: StateFlow<Map<SearchTab, List<Any>>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow<Map<SearchTab, Boolean>>(emptyMap())
    val isLoading: StateFlow<Map<SearchTab, Boolean>> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<Map<SearchTab, String?>>(emptyMap())
    val errorMessage: StateFlow<Map<SearchTab, String?>> = _errorMessage.asStateFlow()

    private val currentPages = mutableMapOf<SearchTab, Int>()
    private val isEnds = mutableMapOf<SearchTab, Boolean>()

    fun performSearch(query: String) {
        if (_searchQuery.value == query && _results.value.isNotEmpty()) return
        _searchQuery.value = query
        SearchTab.entries.forEach {
            currentPages[it] = 1
            isEnds[it] = false
        }
        _results.value = emptyMap()
        fetchData(_currentTab.value)
    }

    fun switchTab(tab: SearchTab) {
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        if (_searchQuery.value.isNotEmpty() && _results.value[tab].isNullOrEmpty()) {
            fetchData(tab)
        }
    }

    fun loadMore(tab: SearchTab) {
        if (_isLoading.value[tab] == true || isEnds[tab] == true || _searchQuery.value.isEmpty()) return
        currentPages[tab] = (currentPages[tab] ?: 1) + 1
        fetchData(tab)
    }

    private fun fetchData(tab: SearchTab) {
        val query = _searchQuery.value
        if (query.isEmpty()) return

        _isLoading.value = _isLoading.value.toMutableMap().apply { put(tab, true) }
        _errorMessage.value = _errorMessage.value.toMutableMap().apply { put(tab, null) }
        val page = currentPages[tab] ?: 1

        viewModelScope.launch {
            try {
                if (tab == SearchTab.VIDEO) {
                    val res = SearchApi.search(query, page)
                    if (res != null) {
                        val items = SearchApi.getVideosFromSearchResult(res, false)
                        if (items.isEmpty()) isEnds[tab] = true
                        
                        val filteredItems = items.filter { it.bvid.isNotEmpty() || it.aid > 0 }
                        val currentList = _results.value[tab] ?: emptyList()
                        _results.value = _results.value.toMutableMap().apply { put(tab, currentList + filteredItems) }
                    } else {
                        isEnds[tab] = true
                    }
                } else {
                    val res = SearchApi.searchType(query, page, tab.type)
                    if (res != null) {
                        val arr = if (tab == SearchTab.LIVE) {
                            if (res.isJsonObject) res.asJsonObject.get("live_room")?.asJsonArray
                            else if (res.isJsonArray) res.asJsonArray else null
                        } else {
                            if (res.isJsonArray) res.asJsonArray else null
                        }

                        if (arr != null) {
                            val arrList = arr.map { it }
                            val items: List<Any> = when (tab) {
                                SearchTab.LIVE -> SearchApi.getLiveFromSearchResult(arrList)
                                SearchTab.USER -> SearchApi.getUsersFromSearchResult(arrList)
                                SearchTab.ARTICLE -> SearchApi.getArticlesFromSearchResult(arrList)
                                SearchTab.BANGUMI -> SearchApi.getBangumisFromSearchResult(arrList)
                                else -> emptyList()
                            }
                            if (items.isEmpty()) isEnds[tab] = true
                            val currentList = _results.value[tab] ?: emptyList()
                            _results.value = _results.value.toMutableMap().apply { put(tab, currentList + items) }
                        } else {
                            isEnds[tab] = true
                        }
                    } else {
                        isEnds[tab] = true
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = _errorMessage.value.toMutableMap().apply { put(tab, e.message ?: "Unknown error") }
            } finally {
                _isLoading.value = _isLoading.value.toMutableMap().apply { put(tab, false) }
            }
        }
    }
}
