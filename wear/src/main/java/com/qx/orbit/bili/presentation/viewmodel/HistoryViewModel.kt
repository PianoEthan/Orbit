package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.HistoryApi
import com.qx.orbit.bili.data.model.ApiResult
import com.qx.orbit.bili.data.model.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<VideoCard>>(emptyList())
    val items = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var lastResult = ApiResult()
    private var isFirstLoad = true

    fun loadData() {
        if (_isLoading.value || lastResult.isBottom) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newList = mutableListOf<VideoCard>()
                lastResult = HistoryApi.getHistory(lastResult, newList)
                
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
        lastResult = ApiResult()
        isFirstLoad = true
        loadData()
    }

    fun deleteHistoryItem(kid: String) {
        viewModelScope.launch {
            try {
                val res = HistoryApi.deleteHistory(kid)
                if (res.code == 0) {
                    _items.value = _items.value.filter { it.kid != kid }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
