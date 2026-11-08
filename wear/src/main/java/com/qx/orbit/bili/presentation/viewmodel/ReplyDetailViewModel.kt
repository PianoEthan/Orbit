package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.model.Reply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class ReplyDetailViewModel(
    private val loadReplyPage: suspend (Long, Long, Int, Int) -> ReplyApi.ReplyPage = { oid, root, page, type ->
        ReplyApi.getRepliesPage(oid = oid, rpid = root, pageNumber = page, type = type)
    }
) : ViewModel() {
    private val _rootReply = MutableStateFlow<Reply?>(null)
    val rootReply: StateFlow<Reply?> = _rootReply.asStateFlow()

    private val _childReplies = MutableStateFlow<List<Reply>>(emptyList())
    val childReplies: StateFlow<List<Reply>> = _childReplies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var page = 1
    private var oid: Long = 0
    private var rpid: Long = 0
    private var replyType: Int = ReplyApi.REPLY_TYPE_VIDEO
    private var childReplyType: Int = ReplyApi.REPLY_TYPE_VIDEO
    private var loadJob: Job? = null
    private var generation = 0
    private val _hasMoreReplies = MutableStateFlow(true)
    val hasMoreReplies: StateFlow<Boolean> = _hasMoreReplies.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private val _isEmoteLoading = MutableStateFlow(false)

    fun initData(root: Reply) {
        if (_rootReply.value?.rpid == root.rpid && oid == root.oid && _rootReply.value?.isDynamic == root.isDynamic) return
        loadJob?.cancel()
        _isLoading.value = false
        _rootReply.value = root
        oid = root.oid
        rpid = root.rpid
        replyType = if (root.isDynamic) ReplyApi.REPLY_TYPE_DYNAMIC else ReplyApi.REPLY_TYPE_VIDEO
        childReplyType = if (root.isDynamic) ReplyApi.REPLY_TYPE_DYNAMIC_CHILD else ReplyApi.REPLY_TYPE_VIDEO
        page = 1
        _hasMoreReplies.value = true
        _errorMessage.value = null
        _childReplies.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (_isLoading.value || !_hasMoreReplies.value || oid == 0L || rpid == 0L) return
        val requestGeneration = ++generation
        val requestOid = oid
        val requestRoot = rpid
        val requestPage = page
        val requestType = childReplyType
        _isLoading.value = true
        _errorMessage.value = null
        loadJob = viewModelScope.launch {
            try {
                val result = loadReplyPage(requestOid, requestRoot, requestPage, requestType)
                if (generation != requestGeneration) return@launch
                val previous = if (requestPage == 1) emptyList() else _childReplies.value
                val merged = (previous + result.replies).distinctBy { it.rpid }
                _childReplies.value = merged
                _hasMoreReplies.value = result.hasMore && (requestPage == 1 || merged.size > previous.size)
                page = requestPage + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) _errorMessage.value = e.message ?: "加载评论失败"
            } finally {
                if (generation == requestGeneration) _isLoading.value = false
            }
        }
    }

    fun likeRootReply(isLiked: Boolean) {
        val root = _rootReply.value ?: return
        viewModelScope.launch {
            try {
                val action = if (isLiked) 0 else 1
                val result = ReplyApi.likeReply(root.oid, root.rpid, action)
                if (result == 0) {
                    _rootReply.value = root.copy(
                        liked = !isLiked,
                        likeCount = root.likeCount + (if (isLiked) -1 else 1)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun likeChildReply(childRpid: Long, isLiked: Boolean) {
        val root = _rootReply.value ?: return
        viewModelScope.launch {
            try {
                val action = if (isLiked) 0 else 1
                val result = ReplyApi.likeReply(root.oid, childRpid, action)
                if (result == 0) {
                    _childReplies.value = _childReplies.value.map { reply ->
                        if (reply.rpid == childRpid) {
                            reply.copy(
                                liked = !isLiked,
                                likeCount = reply.likeCount + (if (isLiked) -1 else 1)
                            )
                        } else {
                            reply
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun sendReply(text: String, target: Reply?) {
        viewModelScope.launch {
            try {
                val parentId = target?.rpid ?: rpid
                val (code, reply) = ReplyApi.sendReply(
                    oid = oid,
                    root = rpid,
                    parent = parentId,
                    text = text,
                    type = replyType
                )
                if (code == 0) {
                    loadJob?.cancel()
                    _isLoading.value = false
                    page = 1
                    _hasMoreReplies.value = true
                    loadMore()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeReplyLocally(reply: Reply) {
        _childReplies.value = _childReplies.value.filter { it.rpid != reply.rpid }
    }
}
