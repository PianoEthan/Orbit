package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliMessageGateway
import com.qx.orbit.bili.core.api.BilibiliPrivateMsgGateway
import com.qx.orbit.bili.core.api.CoreChatSession
import com.qx.orbit.bili.core.api.CoreChatUser
import com.qx.orbit.bili.core.api.CoreNotice
import com.qx.orbit.bili.core.api.CoreNoticeKind
import com.qx.orbit.bili.core.api.CoreUnreadCount
import com.qx.orbit.bili.core.api.MessageGateway
import com.qx.orbit.bili.core.api.PrivateMsgGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisionMessageTab(val title: String) {
    CHAT("私信"), REPLY("回复"), LIKE("点赞"), AT("@我"), SYSTEM("系统"),
}

private val VisionMessageTab.noticeKind: CoreNoticeKind?
    get() = when (this) {
        VisionMessageTab.REPLY -> CoreNoticeKind.REPLY
        VisionMessageTab.LIKE -> CoreNoticeKind.LIKE
        VisionMessageTab.AT -> CoreNoticeKind.AT
        else -> null
    }

data class VisionMessageCenterState(
    val tab: VisionMessageTab = VisionMessageTab.CHAT,
    val unread: CoreUnreadCount = CoreUnreadCount(),
    val sessions: List<CoreChatSession> = emptyList(),
    val sessionUsers: Map<Long, CoreChatUser> = emptyMap(),
    val notices: List<CoreNotice> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
)

class VisionMessageCenterViewModel(
    private val gateway: MessageGateway = BilibiliMessageGateway(),
    private val privateGateway: PrivateMsgGateway = BilibiliPrivateMsgGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionMessageCenterState())
    val state: StateFlow<VisionMessageCenterState> = _state.asStateFlow()

    private var loadedTabs = mutableSetOf<VisionMessageTab>()
    private val tabCursors = mutableMapOf<VisionMessageTab, Pair<Long, Long>>()
    private var job: Job? = null
    private var generation = 0

    fun refresh() {
        generation++
        loadedTabs = mutableSetOf()
        tabCursors.clear()
        viewModelScope.launch {
            val token = generation
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val unread = gateway.loadUnread(VisionUserSession.cookie)
                if (token == generation) _state.value = _state.value.copy(unread = unread)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            loadTab(_state.value.tab, token)
        }
    }

    fun selectTab(tab: VisionMessageTab) {
        if (_state.value.tab == tab && loadedTabs.contains(tab)) return
        _state.value = _state.value.copy(tab = tab, notices = emptyList(), error = null)
        tabCursors.remove(tab)
        loadTab(tab, generation)
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        loadTab(_state.value.tab, generation)
    }

    fun retry() {
        if (_state.value.loading) return
        loadTab(_state.value.tab, generation)
    }

    private fun loadTab(tab: VisionMessageTab, token: Int) {
        job?.cancel()
        val (cursorId, cursorTime) = tabCursors[tab] ?: (0L to 0L)
        val firstPage = cursorId == 0L && cursorTime == 0L
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                if (tab == VisionMessageTab.CHAT) {
                    val sessions = privateGateway.loadSessions(20, VisionUserSession.cookie)
                        .sortedByDescending { it.lastMessage?.timestamp ?: 0L }
                    val uids = (sessions.map { it.talkerId } + _state.value.sessionUsers.keys).distinct()
                    val users = privateGateway.loadUsers(uids, VisionUserSession.cookie).associateBy { it.mid }
                    if (token != generation) return@launch
                    _state.value = _state.value.copy(
                        sessions = sessions,
                        sessionUsers = users,
                        loading = false,
                        hasMore = false,
                    )
                } else {
                    val kind = tab.noticeKind ?: return@launch
                    val page = gateway.loadNotices(kind, cursorId, cursorTime, VisionUserSession.cookie)
                    if (token != generation) return@launch
                    val combined = if (firstPage) page.items else _state.value.notices + page.items
                    _state.value = _state.value.copy(
                        notices = combined,
                        loading = false,
                        hasMore = !page.isEnd && page.items.isNotEmpty(),
                    )
                    if (!page.isEnd) tabCursors[tab] = page.nextId to page.nextTime
                }
                loadedTabs += tab
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == generation) {
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "消息加载失败")
                }
            }
        }
    }
}

class VisionUnreadViewModel(
    private val gateway: MessageGateway = BilibiliMessageGateway(),
) : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        if (VisionUserSession.cookie.isBlank()) {
            _count.value = 0
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val unread = gateway.loadUnread(VisionUserSession.cookie)
                _count.value = unread.total
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }
}
