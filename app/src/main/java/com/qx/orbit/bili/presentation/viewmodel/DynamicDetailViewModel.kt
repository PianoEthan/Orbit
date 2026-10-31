package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.BiliApiService
import com.qx.orbit.bili.data.api.DynamicApi
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.withReplyTopState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

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

    private val _focusedReplyId = MutableStateFlow(0L)
    val focusedReplyId: StateFlow<Long> = _focusedReplyId.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private var replyNext: String? = null
    private val _hasMoreReplies = MutableStateFlow(true)
    val hasMoreReplies: StateFlow<Boolean> = _hasMoreReplies.asStateFlow()
    private val _replyError = MutableStateFlow<String?>(null)
    val replyError: StateFlow<String?> = _replyError.asStateFlow()
    private var loadJob: Job? = null
    private var replyJob: Job? = null
    private var replyGeneration = 0
    private var loadingDynamicId: String? = null
    private var focusedTarget = 0L to 0L

    fun loadDynamic(dynamicId: String, commentRootId: Long = 0L, commentReplyId: Long = 0L) {
        if (_dynamic.value?.dynamicId == dynamicId || (_isLoading.value && loadingDynamicId == dynamicId)) return
        loadJob?.cancel()
        replyJob?.cancel()
        replyGeneration++
        loadingDynamicId = dynamicId
        _dynamic.value = null
        _replies.value = emptyList()
        _focusedReplyId.value = 0L
        _hasMoreReplies.value = true
        replyNext = null
        _isReplyLoading.value = false
        _replyError.value = null
        _isLoading.value = true
        _error.value = null
        loadJob = viewModelScope.launch {
            try {
                val data = DynamicApi.getDynamic(dynamicId)
                if (data != null) {
                    _dynamic.value = data
                    loadReplies(reset = true, focusedRootId = commentRootId, focusedReplyId = commentReplyId)
                } else {
                    _error.value = "无法加载动态"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "网络错误"
            } finally {
                if (loadingDynamicId == dynamicId) _isLoading.value = false
            }
        }
    }

    fun loadReplies(
        reset: Boolean = false,
        focusedRootId: Long = 0L,
        focusedReplyId: Long = 0L
    ) {
        val dyn = _dynamic.value ?: return
        if (!reset && (_isReplyLoading.value || !_hasMoreReplies.value)) return
        if (reset) {
            replyJob?.cancel()
            _focusedReplyId.value = focusedRootId
            focusedTarget = focusedRootId to focusedReplyId
            replyNext = null
            _hasMoreReplies.value = true
        }
        val requestGeneration = ++replyGeneration
        val cursor = replyNext
        val target = focusedTarget
        _isReplyLoading.value = true
        _replyError.value = null
        replyJob = viewModelScope.launch {
            try {
                val result = ReplyApi.getRepliesLazy(dyn.comment_id, 0, cursor, dyn.comment_type, 1)
                val focusedReply = if (cursor == null && target.first > 0L) {
                    ReplyApi.getReplyDetail(dyn.comment_id, target.first, target.second, dyn.comment_type)
                } else null
                if (replyGeneration != requestGeneration || _dynamic.value?.dynamicId != dyn.dynamicId) return@launch
                val previous = if (cursor == null) emptyList() else _replies.value
                val merged = (previous + listOfNotNull(focusedReply) + result.third).distinctBy { it.rpid }
                replyNext = result.second
                _hasMoreReplies.value = !result.second.isNullOrBlank() && result.second != cursor &&
                    (cursor == null || merged.size > previous.size)
                _replies.value = merged
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (replyGeneration == requestGeneration) _replyError.value = e.message ?: "加载评论失败"
            } finally {
                if (replyGeneration == requestGeneration && _dynamic.value?.dynamicId == dyn.dynamicId) _isReplyLoading.value = false
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

    fun onReposted() {
        _dynamic.value = _dynamic.value?.withRepostAdded()
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

    fun updateReplyTop(reply: Reply, isTop: Boolean) {
        _replies.value = _replies.value.withReplyTopState(reply.rpid, isTop)
    }
}
