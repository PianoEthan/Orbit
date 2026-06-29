package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.DynamicApi
import com.qx.orbit.bili.data.api.UserInfoApi
import com.qx.orbit.bili.data.model.ArticleCard
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSpaceViewModel : ViewModel() {
    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed.asStateFlow()

    private val _dynamics = MutableStateFlow<List<Dynamic>>(emptyList())
    val dynamics: StateFlow<List<Dynamic>> = _dynamics.asStateFlow()
    private var dynamicOffset: String = ""
    private var isDynamicEnd = false
    private val _isLoadingDynamics = MutableStateFlow(false)
    val isLoadingDynamics = _isLoadingDynamics.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoCard>>(emptyList())
    val videos: StateFlow<List<VideoCard>> = _videos.asStateFlow()
    private var videoPage = 1
    private var isVideoEnd = false
    private val _isLoadingVideos = MutableStateFlow(false)
    val isLoadingVideos = _isLoadingVideos.asStateFlow()

    private val _articles = MutableStateFlow<List<ArticleCard>>(emptyList())
    val articles: StateFlow<List<ArticleCard>> = _articles.asStateFlow()
    private var articlePage = 1
    private var isArticleEnd = false
    private val _isLoadingArticles = MutableStateFlow(false)
    val isLoadingArticles = _isLoadingArticles.asStateFlow()

    var mid: Long = 0L

    fun initUser(mid: Long) {
        if (this.mid == mid) return
        this.mid = mid
        viewModelScope.launch {
            val info = UserInfoApi.getUserInfo(mid)
            _userInfo.value = info
            _isFollowed.value = info?.followed ?: false
        }
        loadMoreDynamics()
        loadMoreVideos()
        loadMoreArticles()
    }

    fun toggleFollow() {
        val info = _userInfo.value ?: return
        val currentFollowed = _isFollowed.value
        viewModelScope.launch {
            _isFollowed.value = !currentFollowed
            val fansDelta = if (currentFollowed) -1 else 1
            _userInfo.value = info.copy(fans = (info.fans + fansDelta).coerceAtLeast(0))
            try {
                val result = UserInfoApi.followUser(info.mid, !currentFollowed)
                if (result != 0) {
                    _isFollowed.value = currentFollowed
                    _userInfo.value = info
                }
            } catch (e: Exception) {
                _isFollowed.value = currentFollowed
                _userInfo.value = info
            }
        }
    }

    fun loadMoreDynamics() {
        if (_isLoadingDynamics.value || isDynamicEnd) return
        _isLoadingDynamics.value = true
        viewModelScope.launch {
            try {
                val pair = DynamicApi.getDynamicList(offset = dynamicOffset, mid = mid, type = 0)
                val nextOffset = pair.first
                val items = pair.second
                if (items.isEmpty()) {
                    isDynamicEnd = true
                } else {
                    _dynamics.value = _dynamics.value + items
                    dynamicOffset = nextOffset.toString()
                    if (nextOffset == 0L) isDynamicEnd = true
                }
            } catch (e: Exception) {
            } finally {
                _isLoadingDynamics.value = false
            }
        }
    }

    fun loadMoreVideos() {
        if (_isLoadingVideos.value || isVideoEnd) return
        _isLoadingVideos.value = true
        viewModelScope.launch {
            try {
                val pair = UserInfoApi.getUserVideos(mid, videoPage)
                val items = pair.second
                if (items.isEmpty()) {
                    isVideoEnd = true
                } else {
                    _videos.value = _videos.value + items
                    videoPage++
                }
            } catch (e: Exception) {
            } finally {
                _isLoadingVideos.value = false
            }
        }
    }

    fun loadMoreArticles() {
        if (_isLoadingArticles.value || isArticleEnd) return
        _isLoadingArticles.value = true
        viewModelScope.launch {
            try {
                val pair = UserInfoApi.getUserArticles(mid, articlePage)
                val items = pair.second
                if (items.isEmpty()) {
                    isArticleEnd = true
                } else {
                    _articles.value = _articles.value + items
                    articlePage++
                }
            } catch (e: Exception) {
            } finally {
                _isLoadingArticles.value = false
            }
        }
    }
}
