package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.ArticleApi
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.ArticleInfo
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.withReplyTopState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArticleDetailViewModel : ViewModel() {
    private val _article = MutableStateFlow<ArticleInfo?>(null)
    val article: StateFlow<ArticleInfo?> = _article.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _replies = MutableStateFlow<List<Reply>>(emptyList())
    val replies: StateFlow<List<Reply>> = _replies.asStateFlow()

    private val _isReplyLoading = MutableStateFlow(false)
    val isReplyLoading: StateFlow<Boolean> = _isReplyLoading.asStateFlow()

    private val _replyCount = MutableStateFlow(0)
    val replyCount: StateFlow<Int> = _replyCount.asStateFlow()

    private val _focusedReplyId = MutableStateFlow(0L)
    val focusedReplyId: StateFlow<Long> = _focusedReplyId.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private var replyNext: String? = null
    private var hasMoreReplies = true

    fun loadArticle(id: Long, commentRootId: Long = 0L, commentReplyId: Long = 0L) {
        viewModelScope.launch {
            _error.value = null
            replyNext = null
            hasMoreReplies = true
            _replies.value = emptyList()
            _focusedReplyId.value = commentRootId
            try {
                val data = ArticleApi.getArticle(id)
                _article.value = data
                if (data != null) {
                    loadReplies(commentRootId, commentReplyId)
                } else {
                    _error.value = "加载失败"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    fun loadReplies(focusedRootId: Long = 0L, focusedReplyId: Long = 0L) {
        if (!hasMoreReplies || _isReplyLoading.value) return
        _isReplyLoading.value = true
        viewModelScope.launch {
            try {
                val data = _article.value ?: return@launch
                val isFirstPage = replyNext == null
                val result = ReplyApi.getRepliesLazy(data.id, 0, replyNext, 12, 1)
                if (isFirstPage) {
                    _replyCount.value = result.first
                }
                val focusedReply = if (isFirstPage && focusedRootId > 0L) {
                    ReplyApi.getReplyDetail(
                        oid = data.id,
                        root = focusedRootId,
                        targetReplyId = focusedReplyId,
                        type = ReplyApi.REPLY_TYPE_ARTICLE
                    )
                } else {
                    null
                }
                replyNext = result.second
                hasMoreReplies = result.second != null && result.second?.isNotBlank() == true
                _replies.value = (_replies.value + listOfNotNull(focusedReply) + result.third)
                    .distinctBy { it.rpid }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isReplyLoading.value = false
            }
        }
    }

    fun likeReply(rpid: Long) {
        val data = _article.value ?: return
        viewModelScope.launch {
            try {
                val reply = _replies.value.find { it.rpid == rpid } ?: return@launch
                val isLiked = reply.liked
                val action = if (isLiked) 0 else 1
                val resp = ReplyApi.likeReply(data.id, rpid, action, 12)
                if (resp == 0) {
                    _replies.value = _replies.value.map {
                        if (it.rpid == rpid) it.copy(
                            liked = !isLiked,
                            likeCount = it.likeCount + (if (isLiked) -1 else 1)
                        ) else it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendReply(text: String, target: Reply?) {
        val data = _article.value ?: return
        viewModelScope.launch {
            try {
                val parentId = target?.rpid ?: 0L
                val root = if (target != null && target.root > 0) target.root else (target?.rpid ?: 0L)
                val (code, _) = ReplyApi.sendReply(
                    oid = data.id,
                    root = root,
                    parent = parentId,
                    text = text,
                    type = 12
                )
                if (code == 0) {
                    replyNext = null
                    hasMoreReplies = true
                    _replies.value = emptyList()
                    loadReplies()
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

    fun toggleLike() {
        val data = _article.value ?: return
        viewModelScope.launch {
            try {
                val isLiked = data.stats?.liked == true
                val resp = ArticleApi.like(data.id, if (isLiked) 2 else 1)
                if (resp == 0) {
                    _article.value = data.copy(
                        stats = data.stats?.copy(
                            liked = !isLiked,
                            like = data.stats.like + (if (isLiked) -1 else 1)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
