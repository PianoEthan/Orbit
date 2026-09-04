package com.qx.orbit.bili.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.DynamicApi
import com.qx.orbit.bili.data.api.UserInfoApi
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.api.EmoteApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DynamicFeedViewModel(application: Application) : AndroidViewModel(application) {

    private val _upList = MutableStateFlow<List<DynamicApi.UpInfo>>(emptyList())
    val upList: StateFlow<List<DynamicApi.UpInfo>> = _upList.asStateFlow()

    private val _liveList = MutableStateFlow<List<DynamicApi.LiveUserItem>>(emptyList())
    val liveList: StateFlow<List<DynamicApi.LiveUserItem>> = _liveList.asStateFlow()

    private val _dynamicList = MutableStateFlow<List<Dynamic>>(emptyList())
    val dynamicList: StateFlow<List<Dynamic>> = _dynamicList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var selectedMid = MutableStateFlow<Long>(0L)
        private set

    private var currentOffset = 0L
    private var hasMore = true

    var emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
        private set

    init {
        refresh()
        loadEmotes()
    }

    private fun loadEmotes() {
        viewModelScope.launch {
            try {
                emotes.value = EmoteApi.getEmotes(business = "dynamic")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun publishDynamic(
        text: String,
        images: List<java.io.File>? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val success = DynamicApi.publishDynamic(text, emotes.value, images)
                if (success) {
                    onSuccess()
                    refresh() // Refresh the feed to show the new dynamic
                } else {
                    onError("发布失败")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.message ?: "发布出错")
            }
        }
    }

    fun selectUp(mid: Long) {
        if (selectedMid.value == mid) return
        selectedMid.value = mid
        _upList.value = _upList.value.map {
            if (it.mid == mid) it.copy(has_update = false) else it
        }
        viewModelScope.launch {
            UserInfoApi.getUserInfo(mid)
        }
        refreshFeedOnly()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            
            // Load UpList
            try {
                val pair = DynamicApi.getRecentUpList()
                // Sort list: has_update true first
                _upList.value = pair.first.sortedByDescending { it.has_update }
                _liveList.value = pair.second
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Load Feed
            currentOffset = 0L
            hasMore = true
            loadFeed(isRefresh = true)
            
            _isRefreshing.value = false
        }
    }
    
    private fun refreshFeedOnly() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            currentOffset = 0L
            hasMore = true
            loadFeed(isRefresh = true)
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value || !hasMore) return
        viewModelScope.launch {
            _isLoading.value = true
            loadFeed(isRefresh = false)
            _isLoading.value = false
        }
    }

    fun removeDynamicLocally(dynamic: Dynamic) {
        _dynamicList.value = _dynamicList.value.filterNot { it.dynamicId == dynamic.dynamicId }
    }

    private suspend fun loadFeed(isRefresh: Boolean) {
        try {
            _errorMessage.value = null
            val mid = selectedMid.value
            val offsetStr = if (currentOffset == 0L) "" else currentOffset.toString()
            val res = DynamicApi.getDynamicList(offset = offsetStr, mid = mid, type = 0)
            
            val newOffset = res.first
            val items = res.second
            
            if (isRefresh) {
                _dynamicList.value = items
            } else {
                _dynamicList.value = _dynamicList.value + items
            }
            
            if (newOffset == 0L || items.isEmpty()) {
                hasMore = false
            } else {
                currentOffset = newOffset
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = e.message ?: "加载失败"
        }
    }
}
