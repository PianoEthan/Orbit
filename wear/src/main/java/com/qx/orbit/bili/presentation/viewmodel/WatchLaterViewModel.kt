package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.WatchLaterApi
import com.qx.orbit.bili.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchLaterViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<VideoCard>>(emptyList())
    val items = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var isFirstLoad = true

    fun loadData() {
        if (_isLoading.value || !isFirstLoad) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newList = WatchLaterApi.getWatchLater()
                
                if (isFirstLoad) {
                    _items.value = newList
                    isFirstLoad = false
                } else {
                    _items.value = _items.value + newList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refresh() {
        isFirstLoad = true
        loadData()
    }

    fun deleteItem(aid: Long) {
        viewModelScope.launch {
            try {
                val res = WatchLaterApi.deleteWatchLater(aid)
                if (res.code == 0) {
                    _items.value = _items.value.filter { it.aid != aid }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
