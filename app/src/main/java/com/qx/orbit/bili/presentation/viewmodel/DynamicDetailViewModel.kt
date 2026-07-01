package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.BiliApiService
import com.qx.orbit.bili.data.api.DynamicApi
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.model.Reply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DynamicDetailViewModel : ViewModel() {
    private val _dynamic = MutableStateFlow<Dynamic?>(null)
    val dynamic: StateFlow<Dynamic?> = _dynamic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _replies = MutableStateFlow<List<Reply>>(emptyList())
    val replies: StateFlow<List<Reply>> = _replies.asStateFlow()

    private val _isReplyLoading = MutableStateFlow(false)
    val isReplyLoading: StateFlow<Boolean> = _isReplyLoading.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private var replyNext: String? = null
    private var hasMoreReplies = true

    fun loadDynamic(dynamicId: String) {
        if (_dynamic.value?.dynamicId == dynamicId) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val data = DynamicApi.getDynamic(dynamicId)
                if (data != null) {
                    _dynamic.value = data
                    loadReplies(true)
                } else {
                    _error.value = "无法加载动态"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.localizedMessage ?: "网络错误"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadReplies(reset: Boolean = false) {
        if (_isReplyLoading.value) return
        viewModelScope.launch {
            _isReplyLoading.value = true
            try {
                if (reset) {
                    _replies.value = emptyList()
                    replyNext = null
                    hasMoreReplies = true
                }
                if (!hasMoreReplies) {
                    _isReplyLoading.value = false
                    return@launch
                }
                val dyn = _dynamic.value ?: return@launch
                val result = ReplyApi.getRepliesLazy(dyn.comment_id, 0, replyNext, dyn.comment_type, 1)
                replyNext = result.second
                hasMoreReplies = result.second != null && result.second?.isNotBlank() == true
                val newReplies = _replies.value.toMutableList().apply { addAll(result.third) }
                _replies.value = newReplies
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isReplyLoading.value = false
            }
        }
    }

    fun likeReply(rpid: Long, isLiked: Boolean) {
        val dyn = _dynamic.value ?: return
        viewModelScope.launch {
            val action = if (isLiked) 0 else 1 // 1 for like, 0 for cancel
            // Optimistic update
            _replies.value = _replies.value.map { reply ->
                if (reply.rpid == rpid) {
                    reply.copy(
                        liked = !isLiked,
                        likeCount = reply.likeCount + (if (isLiked) -1 else 1)
                    )
                } else {
                    reply
                }
            }
            try {
                val result = ReplyApi.likeReply(dyn.comment_id, rpid, action, type = dyn.comment_type)
                if (result != 0) {
                    // Revert if failed
                    _replies.value = _replies.value.map { reply ->
                        if (reply.rpid == rpid) {
                            reply.copy(
                                liked = isLiked,
                                likeCount = reply.likeCount + (if (isLiked) 1 else -1)
                            )
                        } else {
                            reply
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Revert on exception
                _replies.value = _replies.value.map { reply ->
                    if (reply.rpid == rpid) {
                        reply.copy(
                            liked = isLiked,
                            likeCount = reply.likeCount + (if (isLiked) 1 else -1)
                        )
                    } else {
                        reply
                    }
                }
            }
        }
    }

    fun toggleLike() {
        val dyn = _dynamic.value ?: return
        viewModelScope.launch {
            try {
                val isLiked = dyn.stats?.liked == true
                val action = !isLiked
                val resp = DynamicApi.likeDynamic(dyn.dynamicId, action)
                if (resp == 0) {
                    val newStats = dyn.stats?.copy(
                        liked = !isLiked,
                        like = dyn.stats.like + (if (isLiked) -1 else 1)
                    )
                    _dynamic.value = dyn.copy(stats = newStats)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadEmotes() {
        if (_emotes.value != null) return
        viewModelScope.launch {
            try {
                _emotes.value = EmoteApi.getEmotes(EmoteApi.BUSINESS_REPLY)
            } catch (_: Exception) {}
        }
    }

    fun sendReply(text: String, root: Long = 0, parent: Long = 0, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val dyn = _dynamic.value ?: return
        viewModelScope.launch {
            try {
                val (code, _) = ReplyApi.sendReply(
                    oid = dyn.comment_id,
                    root = root,
                    parent = parent,
                    text = text,
                    type = dyn.comment_type
                )
                if (code == 0) {
                    onSuccess()
                    loadReplies(reset = true)
                } else {
                    onError("发送失败: $code")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "发送失败")
            }
        }
    }

    fun removeReplyLocally(reply: Reply) {
        _replies.value = _replies.value.filter { it.rpid != reply.rpid }
    }
}
