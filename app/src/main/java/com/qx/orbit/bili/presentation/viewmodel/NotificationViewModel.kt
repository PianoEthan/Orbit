package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.MessageApi
import com.qx.orbit.bili.data.model.MessageCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NotificationType(val routeValue: String, val title: String) {
    Reply("reply", "回复我的"),
    Like("like", "收到的赞"),
    At("at", "@我"),
    System("system", "系统通知");

    companion object {
        fun fromRoute(value: String): NotificationType =
            entries.firstOrNull { it.routeValue == value } ?: Reply
    }
}

data class NotificationUiState(
    val type: NotificationType = NotificationType.Reply,
    val items: List<MessageCard> = emptyList(),
    val isLoading: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null
)

class NotificationViewModel : ViewModel() {
    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    private var cursor: MessageCard.Cursor? = null
    private var initialized = false

    fun load(type: NotificationType) {
        if (!initialized || _state.value.type != type) {
            initialized = true
            cursor = null
            _state.value = NotificationUiState(type = type)
            loadMore()
        }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) {
            cursor = null
            _state.value = _state.value.copy(isEnd = false, errorMessage = null)
        }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isEnd) return
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, errorMessage = null)
            try {
                val type = _state.value.type
                val result = when (type) {
                    NotificationType.Like -> MessageApi.getLikeMsg(cursor?.id ?: 0L, cursor?.time ?: 0L)
                    NotificationType.Reply -> MessageApi.getReplyMsg(cursor?.id ?: 0L, cursor?.time ?: 0L)
                    NotificationType.At -> MessageApi.getAtMsg(cursor?.id ?: 0L, cursor?.time ?: 0L)
                    NotificationType.System -> null to MessageApi.getSystemMsg()
                }
                cursor = result.first
                val newItems = if (type == NotificationType.System) {
                    result.second
                } else {
                    _state.value.items + result.second
                }
                _state.value = _state.value.copy(
                    items = newItems.distinctBy { message ->
                        listOf(message.id, message.timeStamp, message.content, message.targetId)
                    },
                    isLoading = false,
                    isEnd = type == NotificationType.System || cursor?.is_end != false
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "通知加载失败"
                )
            }
        }
    }
}
