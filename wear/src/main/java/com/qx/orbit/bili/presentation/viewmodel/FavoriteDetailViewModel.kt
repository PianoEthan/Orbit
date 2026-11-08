package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.FavoriteApi
import com.qx.orbit.bili.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoriteDetailViewModel : ViewModel() {
    private val _videoList = MutableStateFlow<List<VideoCard>>(emptyList())
    val videoList: StateFlow<List<VideoCard>> = _videoList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private var currentKeyword = ""
    private var currentFid: Long = 0L

    fun initLoad(fid: Long) {
        if (currentFid == fid && _videoList.value.isNotEmpty()) return
        currentFid = fid
        currentPage = 1
        hasMore = true
        _videoList.value = emptyList()
        currentKeyword = ""
        loadMore()
    }

    fun search(keyword: String) {
        currentKeyword = keyword
        currentPage = 1
        hasMore = true
        _videoList.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (_isLoading.value || !hasMore || currentFid == 0L) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (hasMoreResult, videos) = FavoriteApi.getFolderVideosV3(currentFid, currentPage, currentKeyword)
                if (videos.isNotEmpty()) {
                    _videoList.value = _videoList.value + videos
                    currentPage++
                }
                hasMore = hasMoreResult
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteVideo(aid: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val result = FavoriteApi.deleteFavorite(aid, currentFid)
                if (result == 0) {
                    _videoList.value = _videoList.value.filter { it.aid != aid }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
