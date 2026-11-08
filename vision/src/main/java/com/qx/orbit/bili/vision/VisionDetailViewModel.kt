package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliVideoGateway
import com.qx.orbit.bili.core.api.BilibiliUserDataGateway
import com.qx.orbit.bili.core.api.UserDataGateway
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoDetails
import com.qx.orbit.bili.core.api.CoreVideoPage
import com.qx.orbit.bili.core.api.CoreVideoReply
import com.qx.orbit.bili.core.api.CoreVideoRelation
import com.qx.orbit.bili.core.api.VideoGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisionDetailViewModel(
    private val gateway: VideoGateway = BilibiliVideoGateway(),
    private val userGateway: UserDataGateway = BilibiliUserDataGateway(),
) : ViewModel() {
    private val _details = MutableStateFlow<CoreVideoDetails?>(null)
    val details: StateFlow<CoreVideoDetails?> = _details.asStateFlow()

    private val _selectedPage = MutableStateFlow<CoreVideoPage?>(null)
    val selectedPage: StateFlow<CoreVideoPage?> = _selectedPage.asStateFlow()

    private val _replies = MutableStateFlow<List<CoreVideoReply>>(emptyList())
    val replies: StateFlow<List<CoreVideoReply>> = _replies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _repliesLoading = MutableStateFlow(false)
    val repliesLoading: StateFlow<Boolean> = _repliesLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _interactionMessage = MutableStateFlow<String?>(null)
    val interactionMessage: StateFlow<String?> = _interactionMessage.asStateFlow()

    private val _repliesError = MutableStateFlow<String?>(null)
    val repliesError: StateFlow<String?> = _repliesError.asStateFlow()

    private var currentVideo: CoreVideo? = null
    private var generation = 0
    private var loadJob: Job? = null
    private var repliesJob: Job? = null
    private var replyOffset: String? = null
    private var replyRequestGeneration = 0
    private var retryRepliesReset = true
    private val _repliesHasMore = MutableStateFlow(false)
    val repliesHasMore = _repliesHasMore.asStateFlow()
    private var relationJob: Job? = null
    private var accountCookie = ""
    private var relationGeneration = 0
    private val _relation = MutableStateFlow<CoreVideoRelation?>(null)
    val relation = _relation.asStateFlow()
    private val _relationLoading = MutableStateFlow(false)
    val relationLoading = _relationLoading.asStateFlow()
    private val _relationError = MutableStateFlow(false)
    val relationError = _relationError.asStateFlow()

    fun setAccount(cookie: String) {
        if (accountCookie == cookie) return
        accountCookie = cookie
        _relation.value = null
        _interactionMessage.value = null
        refreshRelation()
        repliesJob?.cancel()
        replyRequestGeneration++
        replyOffset = null
        _replies.value = emptyList()
        _repliesHasMore.value = false
        _repliesLoading.value = false
        _repliesError.value = null
        _details.value?.video?.id?.let { loadReplies(it) }
    }

    fun refreshRelation() {
        val request = ++relationGeneration
        relationJob?.cancel()
        _relationError.value = false
        _relationLoading.value = false
        val cookie = accountCookie
        val video = _details.value?.video ?: return
        if (cookie.isBlank()) return
        _relationLoading.value = true
        relationJob = viewModelScope.launch {
            try {
                val result = gateway.loadRelation(video.id, cookie)
                if (request == relationGeneration) _relation.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (request == relationGeneration) _relationError.value = true
            } finally {
                if (request == relationGeneration) _relationLoading.value = false
            }
        }
    }

    fun playbackVideo(): CoreVideo? {
        val resolved = _details.value?.video ?: return null
        val selected = _selectedPage.value ?: return resolved
        return resolved.copy(cid = selected.cid, startPositionMs = if (currentVideo?.cid == selected.cid) currentVideo?.startPositionMs ?: 0 else 0)
    }

    fun load(video: CoreVideo, force: Boolean = false) {
        val same = currentVideo?.bvid == video.bvid
        if (!force && same && (_isLoading.value || _details.value != null)) return
        currentVideo = video
        val requestGeneration = ++generation
        loadJob?.cancel()
        repliesJob?.cancel()
        replyRequestGeneration++
        _repliesHasMore.value = false
        replyOffset = null
        _repliesLoading.value = false
        _repliesError.value = null
        if (!same) {
            relationJob?.cancel()
            relationGeneration++
            _relation.value = null
            _relationLoading.value = false
            _relationError.value = false
            _details.value = null
            _selectedPage.value = null
            _replies.value = emptyList()
            _interactionMessage.value = null
        }
        _isLoading.value = true
        _error.value = null
        loadJob = viewModelScope.launch {
            try {
                val result = gateway.loadDetails(video)
                if (requestGeneration == generation) {
                    _details.value = result
                    val desiredCid = _selectedPage.value?.cid ?: video.cid
                    _selectedPage.value = result.pages.firstOrNull { it.cid == desiredCid } ?: result.pages.firstOrNull()
                    loadReplies(result.video.id)
                    refreshRelation()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (requestGeneration == generation) _error.value = exception.message ?: "详情加载失败"
            } finally {
                if (requestGeneration == generation) _isLoading.value = false
            }
        }
    }

    fun selectPage(page: CoreVideoPage) {
        _selectedPage.value = page
    }

    fun clearInteractionMessage() {
        _interactionMessage.value = null
    }

    private val _interactionPending = MutableStateFlow(false)
    val interactionPending = _interactionPending.asStateFlow()

    fun like(cookie: String, csrf: String) = interact(cookie, csrf) { aid ->
        val target = _relation.value?.liked != true
        if (gateway.likeVideo(aid, target, cookie, csrf)) {
            if (target) "点赞成功" else "已取消点赞"
        } else "点赞未完成，请刷新状态后重试"
    }

    fun favorite(folderId: Long, cookie: String, csrf: String) {
        if (folderId <= 0) return
        interact(cookie, csrf) { aid ->
            if (gateway.favVideo(aid, folderId, cookie, csrf)) "已收藏到所选收藏夹"
            else "收藏未完成，请刷新状态后重试"
        }
    }

    fun addWatchLater(cookie: String, csrf: String) = interact(cookie, csrf) { aid ->
        userGateway.changeWatchLater(aid, true, cookie, csrf)
        "已添加到稍后再看"
    }

    fun coin(cookie: String, csrf: String) = interact(cookie, csrf) { aid ->
        if (gateway.coinVideo(aid, 1, cookie, csrf)) "投币成功" else "投币未完成，请刷新状态后重试"
    }

    fun triple(cookie: String, csrf: String) = interact(cookie, csrf) { aid ->
        val result = gateway.tripleVideo(aid, cookie, csrf)
        if (result.liked && result.coined && result.favorited) "三连成功，已投 ${result.coinCount} 枚硬币"
        else "三连结果：点赞${if (result.liked) "成功" else "未完成"}、投币${if (result.coined) "成功" else "未完成"}、收藏${if (result.favorited) "成功" else "未完成"}"
    }

    private fun interact(cookie: String, csrf: String, action: suspend (Long) -> String) {
        if (_interactionPending.value) return
        if (cookie.isBlank() || csrf.isBlank()) {
            _interactionMessage.value = "请先登录后操作"
            return
        }
        if (accountCookie.isNotBlank() && (accountCookie != cookie || _relationLoading.value || _relation.value == null || _relationError.value)) {
            _interactionMessage.value = "请先刷新账号操作状态"
            return
        }
        val accountAtStart = accountCookie
        val video = _details.value?.video ?: return
        if (video.id <= 0) return
        relationJob?.cancel()
        relationGeneration++
        _interactionPending.value = true
        _interactionMessage.value = null
        viewModelScope.launch {
            try {
                val message = action(video.id)
                if (currentVideo?.bvid == video.bvid && accountCookie == accountAtStart) _interactionMessage.value = message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (currentVideo?.bvid == video.bvid && accountCookie == accountAtStart) _interactionMessage.value = "请求失败，结果未确认；请刷新状态后再操作"
            } finally {
                _interactionPending.value = false
                if (currentVideo?.bvid == video.bvid && accountCookie == accountAtStart) refreshRelation()
            }
        }
    }

    fun refreshReplies() {
        _details.value?.video?.id?.let { loadReplies(it) }
    }

    fun retryReplies() {
        if (_repliesLoading.value) return
        _details.value?.video?.id?.let { loadReplies(it, reset = retryRepliesReset) }
    }

    fun loadMoreReplies() {
        if (_repliesLoading.value || !_repliesHasMore.value) return
        _details.value?.video?.id?.let { loadReplies(it, reset = false) }
    }

    private fun loadReplies(aid: Long, reset: Boolean = true) {
        if (aid <= 0) return
        val offset = if (reset) "" else replyOffset ?: return
        val videoGeneration = generation
        val cookie = accountCookie
        val requestGeneration = ++replyRequestGeneration
        retryRepliesReset = reset
        repliesJob?.cancel()
        _repliesLoading.value = true
        _repliesError.value = null
        repliesJob = viewModelScope.launch {
            try {
                val page = gateway.loadReplyPage(aid, offset, cookie)
                if (requestGeneration == replyRequestGeneration && videoGeneration == generation && cookie == accountCookie) {
                    _replies.value = ((if (reset) emptyList() else _replies.value) + page.replies).distinctBy { it.rpid }
                    replyOffset = page.nextOffset?.takeIf { it.isNotBlank() && it != offset }
                    _repliesHasMore.value = replyOffset != null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == replyRequestGeneration) _repliesError.value = "评论加载失败，请重试"
            } finally {
                if (requestGeneration == replyRequestGeneration) _repliesLoading.value = false
            }
        }
    }

    fun retry() {
        currentVideo?.let { load(it, force = true) }
    }
}
