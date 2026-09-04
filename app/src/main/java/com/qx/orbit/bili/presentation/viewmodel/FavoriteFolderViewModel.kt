package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.FavoriteApi
import com.qx.orbit.bili.data.model.FavoriteFolder
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class FavoriteFolderViewModel : ViewModel() {
    private val _folderList = MutableStateFlow<List<FavoriteFolder>>(emptyList())
    val folderList: StateFlow<List<FavoriteFolder>> = _folderList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _editingFolder = MutableStateFlow<FavoriteFolder?>(null)
    val editingFolder = _editingFolder.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        if (_isLoading.value) return
        val midStr = CookieManager.getInfoFromCookie("DedeUserID")
        val mid = midStr.toLongOrNull()
        if (mid == null || mid == 0L) {
            _errorMessage.value = "未登录，无法获取收藏夹列表"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val folders = FavoriteApi.getFavoriteFolders(mid)
                _folderList.value = folders
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createFolder() {
        if (_isProcessing.value) return
        _actionError.value = null
        _editingFolder.value = FavoriteFolder()
    }

    fun editFolder(folder: FavoriteFolder) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                _editingFolder.value = FavoriteApi.getFolderInfo(folder.mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "获取收藏夹信息失败"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun closeEditor() {
        if (!_isProcessing.value) _editingFolder.value = null
    }

    fun saveFolder(title: String, intro: String, isPrivate: Boolean) {
        val folder = _editingFolder.value ?: return
        if (_isProcessing.value || title.isBlank()) return
        _isProcessing.value = true
        _actionError.value = null
        viewModelScope.launch {
            try {
                val privacy = if (isPrivate) 1 else 0
                val code = if (folder.mediaId == 0L) {
                    FavoriteApi.addFolder(title.trim(), intro, privacy)
                } else {
                    FavoriteApi.editFolder(folder.mediaId, title.trim(), intro, privacy, folder.cover)
                }
                if (code == 0) {
                    _editingFolder.value = null
                    _message.value = "收藏夹已保存"
                    loadData()
                } else {
                    _actionError.value = "保存失败（$code），请重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "保存失败，请重试"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteFolder(folder: FavoriteFolder) {
        if (_isProcessing.value || folder.isDefault) return
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val code = FavoriteApi.deleteFolder(folder.mediaId)
                if (code == 0) {
                    _folderList.value = _folderList.value.filter { it.mediaId != folder.mediaId }
                    _message.value = "收藏夹已删除"
                } else {
                    _message.value = "删除失败（$code），请重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "删除失败，请重试"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
