package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.FavoriteApi
import com.qx.orbit.bili.data.api.LikeCoinFavApi
import com.qx.orbit.bili.data.api.RecommendApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.api.VideoInfoApi
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.api.WatchLaterApi
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.model.VideoInfo
import com.qx.orbit.bili.data.model.withReplyTopState
import com.qx.orbit.bili.data.api.EmoteApi
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.File
import com.qx.orbit.bili.data.model.History
import kotlinx.coroutines.delay

class VideoDetailViewModel(
    private val loadVideoInfo: suspend (String) -> VideoInfo? = { VideoInfoApi.getVideoInfo(it) },
    private val loadTags: suspend (String) -> String = { VideoInfoApi.getTags(it) },
    private val loadRelated: suspend (Long) -> List<VideoCard> = { RecommendApi.getRelated(it) },
    private val loadReplyPage: suspend (Long, Int) -> ReplyApi.ReplyPage = { oid, page -> ReplyApi.getRepliesPage(oid = oid, pageNumber = page) },
    private val loadReplyCount: suspend (Long) -> Long = { ReplyApi.getReplyCount(oid = it) },
    private val loadHistory: suspend (Long, Long) -> History? = { aid, cid -> PlayerApi.getHistoryProgress(aid, cid) },
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) : ViewModel() {
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

    private val _replyErrorMessage = MutableStateFlow<String?>(null)
    val replyErrorMessage: StateFlow<String?> = _replyErrorMessage.asStateFlow()

    private val _focusedReplyId = MutableStateFlow(0L)
    val focusedReplyId: StateFlow<Long> = _focusedReplyId.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private val _isEmoteLoading = MutableStateFlow(false)
    val isEmoteLoading: StateFlow<Boolean> = _isEmoteLoading.asStateFlow()

    var bvid: String = ""
    var aid: Long = 0
    private var replyPage = 1
    private var loaded = false
    private var generation = 0
    private var replyGeneration = 0
    private var loadJob: Job? = null
    private var replyJob: Job? = null
    private var historyRefreshing = false
    private var lastHistoryRequestAt: Long? = null
    private var focusedTarget = 0L to 0L
    private val _hasMoreReplies = MutableStateFlow(true)
    val hasMoreReplies: StateFlow<Boolean> = _hasMoreReplies.asStateFlow()

    fun loadData(
        bvid: String,
        aid: Long,
        commentRootId: Long = 0L,
        commentReplyId: Long = 0L,
        refresh: Boolean = false
    ) {
        val sameVideo = isSameVideo(this.bvid, this.aid, bvid, aid)
        if (sameVideo && !refresh && (_isLoading.value || loaded)) {
            if (loaded) focusReply(commentRootId, commentReplyId)
            return
        }
        val requestGeneration = ++generation
        loadJob?.cancel()
        replyJob?.cancel()
        lastHistoryRequestAt = null
        this.bvid = bvid
        this.aid = aid
        loaded = false
        _isReplyLoading.value = false
        if (!sameVideo) {
            _videoInfo.value = null
            _tags.value = ""
            _relatedVideos.value = emptyList()
            _replies.value = emptyList()
            _replyCount.value = 0
            _focusedReplyId.value = 0L
            focusedTarget = 0L to 0L
            replyPage = 1
            _hasMoreReplies.value = true
            _replyErrorMessage.value = null
        }
        _isLoading.value = true
        _errorMessage.value = null
        loadJob = viewModelScope.launch {
            try {
                val infoDeferred = async { loadVideoInfo(bvid) }
                val tagsDeferred = async { loadTags(bvid) }
                val info = infoDeferred.await() ?: throw Exception("视频信息加载失败")
                if (generation != requestGeneration) return@launch
                this@VideoDetailViewModel.bvid = info.bvid.ifBlank { bvid }
                this@VideoDetailViewModel.aid = info.aid.takeIf { it > 0L } ?: aid
                val relatedDeferred = async { loadRelated(this@VideoDetailViewModel.aid) }
                _videoInfo.value = info
                _tags.value = tagsDeferred.await()
                _relatedVideos.value = relatedDeferred.await()
                loaded = true
                loadReplies(reset = true, focusedRootId = commentRootId, focusedReplyId = commentReplyId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "加载详情失败"
            } finally {
                if (generation == requestGeneration) _isLoading.value = false
            }
        }
    }

    suspend fun refreshHistory() {
        val info = _videoInfo.value ?: return
        val cid = info.cids.firstOrNull() ?: return
        val requestAid = info.aid.takeIf { it > 0L } ?: aid
        if (requestAid <= 0L || historyRefreshing) return
        val requestGeneration = generation
        historyRefreshing = true
        try {
            lastHistoryRequestAt?.let { previous ->
                val remaining = 1_000L - (monotonicMillis() - previous)
                if (remaining > 0L) delay(remaining)
            }
            if (generation != requestGeneration) return
            lastHistoryRequestAt = monotonicMillis()
            val history = loadHistory(requestAid, cid) ?: return
            if (generation == requestGeneration) {
                _videoInfo.value = _videoInfo.value?.copy(history = history)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            historyRefreshing = false
        }
    }

    private fun focusReply(rootId: Long, replyId: Long) {
        val target = rootId to replyId
        if (rootId <= 0L || focusedTarget == target) return
        focusedTarget = target
        _focusedReplyId.value = rootId
        val requestAid = aid
        val requestGeneration = generation
        viewModelScope.launch {
            try {
                val reply = ReplyApi.getReplyDetail(requestAid, rootId, replyId) ?: return@launch
                if (generation == requestGeneration && focusedTarget == target) {
                    _replies.value = (listOf(reply) + _replies.value).distinctBy { it.rpid }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) _replyErrorMessage.value = e.message ?: "定位评论失败"
            }
        }
    }

    fun loadReplies(
        reset: Boolean = false,
        focusedRootId: Long = 0L,
        focusedReplyId: Long = 0L
    ) {
        if (aid <= 0L || (!reset && (_isReplyLoading.value || !_hasMoreReplies.value))) return
        if (reset) {
            replyJob?.cancel()
            replyPage = 1
            _hasMoreReplies.value = true
            _focusedReplyId.value = focusedRootId
            focusedTarget = focusedRootId to focusedReplyId
        }
        val requestGeneration = generation
        val requestReplyGeneration = ++replyGeneration
        val requestAid = aid
        val requestPage = replyPage
        val target = focusedTarget
        _isReplyLoading.value = true
        _replyErrorMessage.value = null
        replyJob = viewModelScope.launch {
            try {
                val result = loadReplyPage(requestAid, requestPage)
                val focusedReply = if (requestPage == 1 && target.first > 0L) {
                    ReplyApi.getReplyDetail(requestAid, target.first, target.second)
                } else null
                val count = if (requestPage == 1) loadReplyCount(requestAid).toInt() else _replyCount.value
                if (generation != requestGeneration || replyGeneration != requestReplyGeneration) return@launch
                val previous = if (requestPage == 1) emptyList() else _replies.value
                val merged = (previous + listOfNotNull(focusedReply) + result.replies).distinctBy { it.rpid }
                _hasMoreReplies.value = result.hasMore && (requestPage == 1 || merged.size > previous.size)
                _replies.value = merged
                _replyCount.value = count
                replyPage = requestPage + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration && replyGeneration == requestReplyGeneration) {
                    _replyErrorMessage.value = e.message ?: "加载评论失败"
                }
            } finally {
                if (generation == requestGeneration && replyGeneration == requestReplyGeneration) _isReplyLoading.value = false
            }
        }
    }

    internal companion object {
        fun isSameVideo(currentBvid: String, currentAid: Long, bvid: String, aid: Long): Boolean =
            (bvid.isNotBlank() && bvid == currentBvid) || (aid > 0L && aid == currentAid)
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
    
    fun doTriple(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val info = _videoInfo.value ?: return
        viewModelScope.launch {
            try {
                val result = LikeCoinFavApi.triple(aid)
                if (result == 0) {
                    val newStats = info.stats?.copy(
                        liked = true,
                        coined = if ((info.stats.coined ?: 0) < 2) (info.stats.coined ?: 0) + 1 else 2,
                        favoured = true,
                        like = if (info.stats.liked == true) info.stats.like else info.stats.like + 1,
                        coin = info.stats.coin + 2,
                        favorite = if (info.stats.favoured == true) info.stats.favorite else info.stats.favorite + 1
                    )
                    _videoInfo.value = info.copy(stats = newStats)
                    withContext(Dispatchers.Main) { onResult(true, "三连成功") }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "三连失败: 错误码 $result") }
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

    fun sendReply(text: String, images: List<File>? = null, root: Long, parent: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val (code, reply) = ReplyApi.sendReply(
                    oid = aid,
                    root = root,
                    parent = parent,
                    text = text,
                    type = ReplyApi.REPLY_TYPE_VIDEO,
                    images = images
                )
                if (code == 0) {
                    if (reply != null) {
                        _replies.value = (listOf(reply) + _replies.value).distinctBy { it.rpid }
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

    fun updateReplyTop(reply: Reply, isTop: Boolean) {
        _replies.value = _replies.value.withReplyTopState(reply.rpid, isTop)
    }

    fun addToWatchLater(context: Context) {
        val aid = _videoInfo.value?.aid ?: return
        viewModelScope.launch {
            try {
                val res = WatchLaterApi.addWatchLater(aid)
                if (res.code == 0) {
                    Toast.makeText(context, "已添加到稍后再看", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "添加失败: ${res.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "网络异常，添加失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
