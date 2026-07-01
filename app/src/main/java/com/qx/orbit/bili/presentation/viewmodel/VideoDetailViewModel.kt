package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.FavoriteApi
import com.qx.orbit.bili.data.api.LikeCoinFavApi
import com.qx.orbit.bili.data.api.RecommendApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.api.VideoInfoApi
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.model.VideoInfo
import com.qx.orbit.bili.data.api.EmoteApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class VideoDetailViewModel : ViewModel() {
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()

    private val _tags = MutableStateFlow<String>("")
    val tags: StateFlow<String> = _tags.asStateFlow()

    private val _replies = MutableStateFlow<List<Reply>>(emptyList())
    val replies: StateFlow<List<Reply>> = _replies.asStateFlow()

    private val _replyCount = MutableStateFlow(0)
    val replyCount: StateFlow<Int> = _replyCount.asStateFlow()

    private val _relatedVideos = MutableStateFlow<List<VideoCard>>(emptyList())
    val relatedVideos: StateFlow<List<VideoCard>> = _relatedVideos.asStateFlow()

    private val _favoriteFolders = MutableStateFlow<List<FavoriteApi.FavFolderUI>?>(null)
    val favoriteFolders: StateFlow<List<FavoriteApi.FavFolderUI>?> = _favoriteFolders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isReplyLoading = MutableStateFlow(false)
    val isReplyLoading: StateFlow<Boolean> = _isReplyLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private val _isEmoteLoading = MutableStateFlow(false)
    val isEmoteLoading: StateFlow<Boolean> = _isEmoteLoading.asStateFlow()

    var bvid: String = ""
    var aid: Long = 0
    private var replyPage = 1

    fun loadData(bvid: String, aid: Long) {
        if (_isLoading.value) return
        this.bvid = bvid
        this.aid = aid

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val infoDeferred = async { VideoInfoApi.getVideoInfo(bvid) }
                val tagsDeferred = async { VideoInfoApi.getTags(bvid) }
                val relatedDeferred = async { RecommendApi.getRelated(aid) }
                
                _videoInfo.value = infoDeferred.await()
                _tags.value = tagsDeferred.await()
                _relatedVideos.value = relatedDeferred.await()
                
                loadReplies(reset = true)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "加载详情失败"
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
                    replyPage = 1
                    _replyCount.value = ReplyApi.getReplyCount(oid = aid).toInt()
                }
                val newReplies = ReplyApi.getReplies(oid = aid, pageNumber = replyPage)
                if (reset) {
                    _replies.value = newReplies
                } else {
                    _replies.value = _replies.value + newReplies
                }
                replyPage++
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isReplyLoading.value = false
            }
        }
    }

    fun likeReply(rpid: Long, isLiked: Boolean) {
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
                val result = ReplyApi.likeReply(aid, rpid, action)
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

    fun toggleLike(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val info = _videoInfo.value ?: return
        viewModelScope.launch {
            try {
                val isLiked = info.stats?.liked == true
                val newAction = if (isLiked) 2 else 1 // 1=like, 2=cancel
                val result = LikeCoinFavApi.like(aid, newAction)
                if (result == 0) {
                    // Update local state
                    val newStats = info.stats?.copy(
                        liked = !isLiked,
                        like = info.stats.like + (if (isLiked) -1 else 1)
                    )
                    _videoInfo.value = info.copy(stats = newStats)
                    withContext(Dispatchers.Main) { onResult(true, if (isLiked) "已取消点赞" else "点赞成功") }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "点赞失败: 错误码 $result") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "操作异常") }
            }
        }
    }
    
    fun doCoin(multiply: Int, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val info = _videoInfo.value ?: return
        viewModelScope.launch {
            try {
                val isCoined = (info.stats?.coined ?: 0) > 0
                if (isCoined) {
                    withContext(Dispatchers.Main) { onResult(false, "已经投过币了") }
                    return@launch
                }
                val result = LikeCoinFavApi.coin(aid, multiply)
                if (result == 0) {
                    val newStats = info.stats?.copy(
                        coined = multiply,
                        coin = info.stats.coin + multiply
                    )
                    _videoInfo.value = info.copy(stats = newStats)
                    withContext(Dispatchers.Main) { onResult(true, "投币成功") }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "投币失败: 错误码 $result") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "操作异常") }
            }
        }
    }
    
    fun loadFavoriteFolders() {
        viewModelScope.launch {
            try {
                val result = FavoriteApi.getFavoriteState(aid)
                _favoriteFolders.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun doFavorite(fid: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val info = _videoInfo.value ?: return
        viewModelScope.launch {
            try {
                val result = FavoriteApi.addFavorite(aid, fid)
                if (result == 0) {
                    val newStats = info.stats?.copy(
                        favoured = true,
                        favorite = info.stats.favorite + 1
                    )
                    _videoInfo.value = info.copy(stats = newStats)
                    // Optionally reload folders
                    loadFavoriteFolders()
                    withContext(Dispatchers.Main) { onResult(true, "收藏成功") }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "收藏失败: 错误码 $result") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "操作异常") }
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
        viewModelScope.launch {
            try {
                val (code, reply) = ReplyApi.sendReply(
                    oid = aid,
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
