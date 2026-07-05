package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.BangumiApi
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FollowListViewModel : ViewModel() {
    private val _videoList = MutableStateFlow<List<VideoCard>>(emptyList())
    val videoList: StateFlow<List<VideoCard>> = _videoList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentPage = 1
    private var hasMore = true

    init {
        loadMore()
    }

    fun loadMore() {
        if (_isLoading.value || !hasMore) return
        val midStr = CookieManager.getInfoFromCookie("DedeUserID")
        val mid = midStr.toLongOrNull()
        if (mid == null || mid == 0L) {
            _errorMessage.value = "未登录，无法获取追番列表"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (status, cards) = BangumiApi.getFollowingList(mid, currentPage)
                if (status == 0) {
                    if (cards.isEmpty()) {
                        hasMore = false
                    } else {
                        _videoList.value = _videoList.value + cards
                        currentPage++
                    }
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unfollowBangumi(seasonId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = BangumiApi.unfollowBangumi(seasonId)
                if (success) {
                    _videoList.value = _videoList.value.filter { it.seasonId != seasonId }
                }
                onResult(success)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
