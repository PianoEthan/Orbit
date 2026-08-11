package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.MessageApi
import com.qx.orbit.bili.data.api.PrivateMsgApi
import com.qx.orbit.bili.data.model.PrivateMsgSession
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageCenterUiState(
    val unread: MessageApi.UnreadData = MessageApi.UnreadData(),
    val sessions: List<PrivateMsgSession> = emptyList(),
    val users: Map<Long, UserInfo> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MessageCenterViewModel : ViewModel() {
    private val _state = MutableStateFlow(MessageCenterUiState())
    val state: StateFlow<MessageCenterUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        if (CookieManager.getMid() <= 0L) {
            _state.value = MessageCenterUiState(errorMessage = "登录后才能查看消息")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val (unread, sessions) = coroutineScope {
                    val unreadRequest = async { MessageApi.getUnread() }
                    val sessionRequest = async { PrivateMsgApi.getSessionsList(30) }
                    unreadRequest.await() to sessionRequest.await()
                }
                val sortedSessions = sessions.sortedWith(
                    compareByDescending<PrivateMsgSession> { if (it.unread > 0) 1 else 0 }
                        .thenByDescending(PrivateMsgSession::timestamp)
                )
                val users = runCatching {
                    PrivateMsgApi.getUsersInfo(sortedSessions.map(PrivateMsgSession::talkerUid))
                }.getOrDefault(emptyMap())
                _state.value = MessageCenterUiState(
                    unread = unread,
                    sessions = sortedSessions,
                    users = users
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "消息加载失败"
                )
            }
        }
    }
}
