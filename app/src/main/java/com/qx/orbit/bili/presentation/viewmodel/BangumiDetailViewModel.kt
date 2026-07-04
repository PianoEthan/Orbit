package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.BangumiApi
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.Bangumi
import com.qx.orbit.bili.data.model.Reply
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BangumiDetailViewModel : ViewModel() {
    private val _bangumiInfo = MutableStateFlow<Bangumi?>(null)
    val bangumiInfo: StateFlow<Bangumi?> = _bangumiInfo.asStateFlow()

    private val _replies = MutableStateFlow<List<Reply>>(emptyList())
    val replies: StateFlow<List<Reply>> = _replies.asStateFlow()

    private val _replyCount = MutableStateFlow(0)
    val replyCount: StateFlow<Int> = _replyCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isReplyLoading = MutableStateFlow(false)
    val isReplyLoading: StateFlow<Boolean> = _isReplyLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _replyErrorMessage = MutableStateFlow<String?>(null)
    val replyErrorMessage: StateFlow<String?> = _replyErrorMessage.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private val _isEmoteLoading = MutableStateFlow(false)
    val isEmoteLoading: StateFlow<Boolean> = _isEmoteLoading.asStateFlow()

    var mediaId: Long = 0
    private var replyPage = 1
    private var commentAid: Long = 0

    fun loadData(mediaId: Long) {
        if (_isLoading.value) return
        this.mediaId = mediaId

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val info = BangumiApi.getBangumi(mediaId)
                if (info != null) {
                    _bangumiInfo.value = info
                    
                    // Try to find an aid for comments (usually the first episode's aid)
                    val firstEpAid = info.sectionList.firstOrNull()?.episodes?.firstOrNull()?.aid ?: 0L
                    if (firstEpAid > 0) {
                        commentAid = firstEpAid
                        loadReplies(reset = true)
                    }
                } else {
                    _errorMessage.value = "无法获取番剧信息"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "加载番剧详情失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadReplies(reset: Boolean = false) {
        if (commentAid <= 0 || _isReplyLoading.value) return
        viewModelScope.launch {
            _isReplyLoading.value = true
            if (reset) _replyErrorMessage.value = null
            try {
                if (reset) {
                    replyPage = 1
                    _replyCount.value = ReplyApi.getReplyCount(oid = commentAid).toInt()
                }
                val newReplies = ReplyApi.getReplies(oid = commentAid, pageNumber = replyPage)
                if (reset) {
                    _replies.value = newReplies
                } else {
                    _replies.value = _replies.value + newReplies
                }
                replyPage++
            } catch (e: Exception) {
                e.printStackTrace()
                _replyErrorMessage.value = e.message ?: "加载评论失败"
            } finally {
                _isReplyLoading.value = false
            }
        }
    }

    fun likeReply(rpid: Long, isLiked: Boolean) {
        if (commentAid <= 0) return
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
                val result = ReplyApi.likeReply(commentAid, rpid, action)
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

    fun loadEmotes() {
        if (_emotes.value != null) return
        _isEmoteLoading.value = true
        viewModelScope.launch {
            try {
                val result = EmoteApi.getEmotes(EmoteApi.BUSINESS_REPLY)
                _emotes.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isEmoteLoading.value = false
            }
        }
    }

    fun sendReply(text: String, root: Long, parent: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (commentAid <= 0) {
            onError("无法获取评论区信息")
            return
        }
        viewModelScope.launch {
            try {
                val (code, reply) = ReplyApi.sendReply(
                    oid = commentAid,
                    root = root,
                    parent = parent,
                    text = text,
                    type = ReplyApi.REPLY_TYPE_VIDEO
                )
                if (code == 0) {
                    if (reply != null) {
                        _replies.value = listOf(reply) + _replies.value
                    } else {
                        loadReplies(reset = true)
                    }
                    onSuccess()
                } else {
                    onError("发送失败 (错误码: $code)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "网络异常，发送失败")
            }
        }
    }

    fun removeReplyLocally(reply: Reply) {
        _replies.value = _replies.value.filter { it.rpid != reply.rpid }
    }
}
