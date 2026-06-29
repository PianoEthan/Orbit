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

class ReplyDetailViewModel : ViewModel() {
    private val _rootReply = MutableStateFlow<Reply?>(null)
    val rootReply: StateFlow<Reply?> = _rootReply.asStateFlow()

    private val _childReplies = MutableStateFlow<List<Reply>>(emptyList())
    val childReplies: StateFlow<List<Reply>> = _childReplies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var page = 1
    private var oid: Long = 0
    private var rpid: Long = 0

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private val _isEmoteLoading = MutableStateFlow(false)

    fun initData(root: Reply) {
        if (_rootReply.value != null && _rootReply.value?.rpid == root.rpid) return
        _rootReply.value = root
        this.oid = root.oid
        this.rpid = root.rpid
        page = 1
        _childReplies.value = emptyList()
        loadMore()
    }

    fun loadMore() {
        if (_isLoading.value || oid == 0L || rpid == 0L) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newReplies = ReplyApi.getReplies(oid = oid, rpid = rpid, pageNumber = page)
                _childReplies.value = _childReplies.value + newReplies
                if (newReplies.isNotEmpty()) page++
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
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
                    type = ReplyApi.REPLY_TYPE_VIDEO
                )
                if (code == 0) {
                    page = 1
                    _childReplies.value = emptyList()
                    loadMore()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
