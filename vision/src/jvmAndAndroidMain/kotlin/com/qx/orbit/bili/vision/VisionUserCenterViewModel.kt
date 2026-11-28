package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.AuthGateway
import com.qx.orbit.bili.core.api.BilibiliAuthGateway
import com.qx.orbit.bili.core.api.BilibiliUserDataGateway
import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.CoreHistoryItem
import com.qx.orbit.bili.core.api.CoreHistoryCursor
import com.qx.orbit.bili.core.api.CoreUserProfile
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.UserDataGateway
import com.qx.orbit.bili.core.api.parseCookieValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UserCenterTab(val title: String) {
    HISTORY("历史记录"), WATCH_LATER("稍后再看"), FAVORITES("我的收藏"), DYNAMIC("动态视频"),
}

class VisionUserCenterViewModel(
    private val userGateway: UserDataGateway = BilibiliUserDataGateway(),
    private val authGateway: AuthGateway = BilibiliAuthGateway(),
    private val cookieProvider: () -> String = { VisionUserSession.cookie },
    private val midProvider: () -> Long = { VisionUserSession.mid },
    private val onProfile: (CoreUserProfile) -> Unit = { VisionUserSession.updateProfile(it) },
) : ViewModel() {
    private val _tab = MutableStateFlow(UserCenterTab.HISTORY)
    val tab = _tab.asStateFlow()
    private val _history = MutableStateFlow<List<CoreHistoryItem>>(emptyList())
    val history = _history.asStateFlow()
    private val _historyHasMore = MutableStateFlow(false)
    val historyHasMore = _historyHasMore.asStateFlow()
    private var nextHistoryCursor: CoreHistoryCursor? = null
    private var retryHistoryReset = true

    fun loadMoreHistory() {
        if (_tab.value != UserCenterTab.HISTORY || _isLoading.value || !_historyHasMore.value) return
        launchRequest { cookie, request -> loadHistoryPage(cookie, request, reset = false) }
    }

    private suspend fun loadHistoryPage(cookie: String, request: Int, reset: Boolean) {
        retryHistoryReset = reset
        val cursor = if (reset) CoreHistoryCursor() else nextHistoryCursor ?: return
        val result = userGateway.loadHistoryPage(cookie, cursor)
        if (!isCurrent(request, cookie)) return
        val previous = if (reset) emptyList() else _history.value
        _history.value = (previous + result.items).distinctBy { it.bvid to it.cid }
        nextHistoryCursor = result.next?.takeIf { it != cursor }
        _historyHasMore.value = nextHistoryCursor != null
    }
    private val _watchLater = MutableStateFlow<List<CoreVideo>>(emptyList())
    val watchLater = _watchLater.asStateFlow()
    private val _favFolders = MutableStateFlow<List<CoreFavFolder>>(emptyList())
    val favFolders = _favFolders.asStateFlow()
    private val _selectedFolder = MutableStateFlow<Long?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()
    private val _favVideos = MutableStateFlow<List<CoreVideo>>(emptyList())
    val favVideos = _favVideos.asStateFlow()
    private val _favHasMore = MutableStateFlow(false)
    val favHasMore = _favHasMore.asStateFlow()
    private val _dynamicVideos = MutableStateFlow<List<CoreVideo>>(emptyList())
    val dynamicVideos = _dynamicVideos.asStateFlow()
    private val _dynamicHasMore = MutableStateFlow(false)
    val dynamicHasMore = _dynamicHasMore.asStateFlow()
    private var nextDynamicOffset: String? = null
    private var retryDynamicReset = true

    fun loadMoreDynamic() {
        if (_tab.value != UserCenterTab.DYNAMIC || _isLoading.value || !_dynamicHasMore.value) return
        launchRequest { cookie, request -> loadDynamicPage(cookie, request, reset = false) }
    }

    private suspend fun loadDynamicPage(cookie: String, request: Int, reset: Boolean) {
        retryDynamicReset = reset
        val offset = if (reset) "" else nextDynamicOffset ?: return
        val result = userGateway.loadDynamicVideoPage(cookie, offset)
        if (!isCurrent(request, cookie)) return
        val previous = if (reset) emptyList() else _dynamicVideos.value
        _dynamicVideos.value = (previous + result.videos).distinctBy { it.bvid }
        nextDynamicOffset = result.nextOffset?.takeIf { it != offset }
        _dynamicHasMore.value = nextDynamicOffset != null
    }
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _removing = MutableStateFlow<Long?>(null)
    val removing = _removing.asStateFlow()
    private val _mutationMessage = MutableStateFlow<String?>(null)
    val mutationMessage = _mutationMessage.asStateFlow()
    private var nextFavPage = 1

    fun removeWatchLater(video: CoreVideo) {
        if (_removing.value != null || _isLoading.value || _tab.value != UserCenterTab.WATCH_LATER) return
        val cookie = cookieProvider()
        val csrf = parseCookieValue(cookie, "bili_jct")
        if (cookie.isBlank() || csrf.isBlank() || video.id <= 0) {
            _mutationMessage.value = "请重新登录后操作"
            return
        }
        _removing.value = video.id
        _mutationMessage.value = null
        job?.cancel()
        val request = ++generation
        viewModelScope.launch {
            try {
                userGateway.changeWatchLater(video.id, false, cookie, csrf)
                if (isCurrent(request, cookie)) {
                    _watchLater.value = _watchLater.value.filterNot { it.id == video.id }
                    _mutationMessage.value = "已从稍后再看移除"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(request, cookie)) _mutationMessage.value = "移除未确认，请刷新后再操作"
            } finally {
                _removing.value = null
            }
        }
    }
    private var job: Job? = null
    private var profileJob: Job? = null
    private var generation = 0
    private var observedCookie: String? = null

    fun onSessionChanged() {
        val cookie = cookieProvider()
        if (observedCookie == cookie) return
        observedCookie = cookie
        generation++
        job?.cancel()
        profileJob?.cancel()
        _history.value = emptyList()
        nextHistoryCursor = null
        _historyHasMore.value = false
        _watchLater.value = emptyList()
        _favFolders.value = emptyList()
        _favVideos.value = emptyList()
        _selectedFolder.value = null
        _favHasMore.value = false
        _dynamicVideos.value = emptyList()
        nextDynamicOffset = null
        _dynamicHasMore.value = false
        _isLoading.value = false
        _error.value = null
        _mutationMessage.value = null
        if (cookie.isNotBlank()) refresh()
    }

    fun selectTab(newTab: UserCenterTab) {
        if (_tab.value == newTab) return
        _tab.value = newTab
        refreshCurrentTab()
    }

    fun refresh() {
        val cookie = cookieProvider()
        profileJob?.cancel()
        if (cookie.isNotBlank()) profileJob = viewModelScope.launch {
            try {
                val profile = authGateway.getMyProfile(cookie)
                if (cookieProvider() == cookie) onProfile(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { }
        }
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        val activeTab = _tab.value
        launchRequest { cookie, request ->
            when (activeTab) {
                UserCenterTab.HISTORY -> loadHistoryPage(cookie, request, reset = true)
                UserCenterTab.WATCH_LATER -> {
                    val result = userGateway.loadWatchLater(cookie)
                    if (isCurrent(request, cookie)) _watchLater.value = result
                }
                UserCenterTab.DYNAMIC -> loadDynamicPage(cookie, request, reset = true)
                UserCenterTab.FAVORITES -> {
                    val folders = userGateway.loadFavFolders(cookie, midProvider())
                    if (!isCurrent(request, cookie)) return@launchRequest
                    _favFolders.value = folders
                    val folderId = _selectedFolder.value?.takeIf { id -> folders.any { it.id == id } } ?: folders.firstOrNull()?.id
                    _selectedFolder.value = folderId
                    _favVideos.value = emptyList()
                    _favHasMore.value = false
                    nextFavPage = 1
                    if (folderId != null) loadFavoritePage(cookie, request, folderId, reset = true)
                }
            }
        }
    }

    fun selectFolder(id: Long) {
        if (_tab.value != UserCenterTab.FAVORITES || id == _selectedFolder.value || _favFolders.value.none { it.id == id }) return
        _selectedFolder.value = id
        _favVideos.value = emptyList()
        _favHasMore.value = false
        nextFavPage = 1
        launchRequest { cookie, request -> loadFavoritePage(cookie, request, id, reset = true) }
    }

    fun loadMoreFavorites() {
        if (_tab.value != UserCenterTab.FAVORITES || _isLoading.value || !_favHasMore.value) return
        val id = _selectedFolder.value ?: return
        launchRequest { cookie, request -> loadFavoritePage(cookie, request, id, reset = false) }
    }

    fun retry() {
        if (_tab.value == UserCenterTab.FAVORITES && _selectedFolder.value != null) {
            val id = _selectedFolder.value ?: return
            launchRequest { cookie, request -> loadFavoritePage(cookie, request, id, reset = nextFavPage == 1) }
        } else if (_tab.value == UserCenterTab.HISTORY) {
            launchRequest { cookie, request -> loadHistoryPage(cookie, request, reset = retryHistoryReset) }
        } else if (_tab.value == UserCenterTab.DYNAMIC) {
            launchRequest { cookie, request -> loadDynamicPage(cookie, request, reset = retryDynamicReset) }
        } else refreshCurrentTab()
    }

    private suspend fun loadFavoritePage(cookie: String, request: Int, folderId: Long, reset: Boolean) {
        val page = if (reset) 1 else nextFavPage
        val result = userGateway.loadFavVideoPage(cookie, folderId, page)
        if (!isCurrent(request, cookie)) return
        val previous = if (reset) emptyList() else _favVideos.value
        val combined = (previous + result.videos).distinctBy { it.bvid }
        _favVideos.value = combined
        _favHasMore.value = result.hasMore && (reset || combined.size > previous.size)
        nextFavPage = page + 1
    }

    private fun isCurrent(request: Int, cookie: String) = request == generation && cookieProvider() == cookie

    private fun launchRequest(block: suspend (String, Int) -> Unit) {
        val cookie = cookieProvider()
        val request = ++generation
        job?.cancel()
        _isLoading.value = false
        _error.value = null
        if (cookie.isBlank()) return
        _isLoading.value = true
        job = viewModelScope.launch {
            try {
                block(cookie, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(request, cookie)) _error.value = "加载失败，请重试"
            } finally {
                if (isCurrent(request, cookie)) _isLoading.value = false
            }
        }
    }
}
