package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.qx.orbit.bili.data.api.PrivateMsgApi
import com.qx.orbit.bili.data.model.PrivateMessage
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.model.displayText
import com.qx.orbit.bili.data.remote.CookieManager
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PrivateChatUiState(
    val talkerId: Long = 0L,
    val user: UserInfo? = null,
    val messages: List<PrivateMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isSending: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val sendErrorMessage: String? = null
)

class PrivateChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(PrivateChatUiState())
    val state: StateFlow<PrivateChatUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun open(talkerId: Long) {
        if (talkerId <= 0L || (_state.value.talkerId == talkerId && _state.value.messages.isNotEmpty())) return
        refreshJob?.cancel()
        _state.value = PrivateChatUiState(talkerId = talkerId, isLoading = true)
        viewModelScope.launch {
            try {
                val page = PrivateMsgApi.getPrivateMsgPage(talkerId, INITIAL_PAGE_SIZE, 0L, 0L)
                val user = runCatching { PrivateMsgApi.getUsersInfo(listOf(talkerId))[talkerId] }.getOrNull()
                _state.value = _state.value.copy(
                    user = user,
                    messages = page.messages.reversed().deduplicated(),
                    isLoading = false,
                    hasMore = page.hasMore
                )
                runCatching { PrivateMsgApi.updateAck(talkerId, 1, 0L) }
                startRefreshLoop(talkerId)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "聊天记录加载失败"
                )
            }
        }
    }

    fun retry() {
        val talkerId = _state.value.talkerId
        _state.value = PrivateChatUiState(talkerId = talkerId)
        open(talkerId)
    }

    fun loadOlder() {
        val current = _state.value
        if (current.isLoading || current.isLoadingOlder || !current.hasMore) return
        val oldestSeqno = current.messages.firstOrNull { it.msgSeqno > 0L }?.msgSeqno ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingOlder = true)
            try {
                val page = PrivateMsgApi.getPrivateMsgPage(
                    current.talkerId,
                    HISTORY_PAGE_SIZE,
                    0L,
                    oldestSeqno
                )
                val older = page.messages.reversed()
                _state.value = _state.value.copy(
                    messages = (older + _state.value.messages).deduplicated(),
                    isLoadingOlder = false,
                    hasMore = page.hasMore
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoadingOlder = false,
                    errorMessage = error.message ?: "更多消息加载失败"
                )
            }
        }
    }

    fun send(text: String, onSuccess: () -> Unit) {
        val messageText = text.trim()
        val current = _state.value
        val selfUid = CookieManager.getMid()
        if (messageText.isEmpty() || current.isSending || selfUid <= 0L) return
        val timestamp = System.currentTimeMillis() / 1000L
        val localId = System.nanoTime()
        val content = JsonObject().apply { addProperty("content", messageText) }
        val pendingMessage = PrivateMessage(
            content = content,
            type = PrivateMessage.TYPE_TEXT,
            timestamp = timestamp,
            uid = selfUid,
            localId = localId,
            isPending = true
        )
        _state.value = current.copy(
            messages = current.messages + pendingMessage,
            isSending = true,
            sendErrorMessage = null
        )
        viewModelScope.launch {
            try {
                PrivateMsgApi.sendMsg(
                    senderUid = selfUid,
                    receiverUid = current.talkerId,
                    msgType = PrivateMessage.TYPE_TEXT,
                    timestamp = timestamp,
                    content = content.toString()
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages.map { message ->
                        if (message.localId == localId) message.copy(isPending = false) else message
                    },
                    isSending = false
                )
                onSuccess()
                delay(SEND_REFRESH_DELAY_MILLIS)
                refreshNewer(current.talkerId)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages.filterNot { it.localId == localId },
                    isSending = false,
                    sendErrorMessage = error.message ?: "发送失败"
                )
            }
        }
    }

    fun clearSendError() {
        _state.value = _state.value.copy(sendErrorMessage = null)
    }

    private fun startRefreshLoop(talkerId: Long) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MILLIS)
                runCatching { refreshNewer(talkerId) }
            }
        }
    }

    private suspend fun refreshNewer(talkerId: Long) {
        if (_state.value.talkerId != talkerId || _state.value.isLoading) return
        val latestSeqno = _state.value.messages.lastOrNull { it.msgSeqno > 0L }?.msgSeqno ?: 0L
        val page = PrivateMsgApi.getPrivateMsgPage(talkerId, INITIAL_PAGE_SIZE, latestSeqno, 0L)
        val incoming = page.messages.reversed()
        if (incoming.isNotEmpty()) {
            _state.value = _state.value.copy(
                messages = mergeIncoming(_state.value.messages, incoming)
            )
            runCatching { PrivateMsgApi.updateAck(talkerId, 1, 0L) }
        }
    }

    private fun mergeIncoming(
        current: List<PrivateMessage>,
        incoming: List<PrivateMessage>
    ): List<PrivateMessage> {
        val merged = current.toMutableList()
        incoming.forEach { serverMessage ->
            if (serverMessage.msgSeqno > 0L && merged.any { it.msgSeqno == serverMessage.msgSeqno }) {
                return@forEach
            }
            val localIndex = merged.indexOfFirst { localMessage ->
                localMessage.localId > 0L &&
                    localMessage.uid == serverMessage.uid &&
                    localMessage.type == serverMessage.type &&
                    localMessage.displayText() == serverMessage.displayText() &&
                    abs(localMessage.timestamp - serverMessage.timestamp) <= LOCAL_MATCH_WINDOW_SECONDS
            }
            if (localIndex >= 0) merged[localIndex] = serverMessage else merged += serverMessage
        }
        return merged.deduplicated()
    }

    private fun List<PrivateMessage>.deduplicated(): List<PrivateMessage> {
        val seenSeqnos = hashSetOf<Long>()
        val seenLocalIds = hashSetOf<Long>()
        return filter { message ->
            when {
                message.msgSeqno > 0L -> seenSeqnos.add(message.msgSeqno)
                message.localId > 0L -> seenLocalIds.add(message.localId)
                else -> true
            }
        }
    }

    private companion object {
        private const val INITIAL_PAGE_SIZE = 50
        private const val HISTORY_PAGE_SIZE = 20
        private const val REFRESH_INTERVAL_MILLIS = 15_000L
        private const val SEND_REFRESH_DELAY_MILLIS = 600L
        private const val LOCAL_MATCH_WINDOW_SECONDS = 10L
    }
}
