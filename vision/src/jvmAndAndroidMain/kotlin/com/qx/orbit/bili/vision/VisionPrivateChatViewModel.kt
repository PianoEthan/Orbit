package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliPrivateMsgGateway
import com.qx.orbit.bili.core.api.CoreChatMessage
import com.qx.orbit.bili.core.api.PrivateMsgGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisionPrivateChatState(
    val messages: List<CoreChatMessage> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val hasOlder: Boolean = false,
    val error: String? = null,
    val sendError: String? = null,
)

class VisionPrivateChatViewModel(
    private val gateway: PrivateMsgGateway = BilibiliPrivateMsgGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionPrivateChatState())
    val state: StateFlow<VisionPrivateChatState> = _state.asStateFlow()

    private var talkerId = 0L
    private var sessionType = 1
    private var oldestSeqno = Long.MAX_VALUE
    private var generation = 0
    private var job: Job? = null

    fun open(talkerId: Long, knownSessionType: Int = 1) {
        if (talkerId == this.talkerId && _state.value.messages.isNotEmpty()) return
        this.talkerId = talkerId
        this.sessionType = knownSessionType
        this.oldestSeqno = Long.MAX_VALUE
        generation++
        _state.value = VisionPrivateChatState()
        loadOlder()
        acknowledge()
    }

    fun loadOlder() {
        if (talkerId <= 0 || _state.value.loading) return
        val token = generation
        val endSeqno = oldestSeqno
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val page = gateway.loadHistory(talkerId, PAGE_SIZE, 0, endSeqno, VisionUserSession.cookie)
                if (token != generation) return@launch
                val merged = (page.messages + _state.value.messages).distinctBy { it.seqno.takeIf { s -> s > 0 } ?: it.hashCode() }
                    .sortedBy { it.timestamp }
                _state.value = _state.value.copy(
                    messages = merged,
                    loading = false,
                    hasOlder = page.hasMore && page.messages.isNotEmpty(),
                )
                page.messages.minOfOrNull { it.seqno }?.let { oldestSeqno = it }
                acknowledge()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "消息加载失败")
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || talkerId <= 0 || _state.value.sending) return
        if (!VisionUserSession.isLoggedIn) {
            _state.value = _state.value.copy(sendError = "请先登录")
            return
        }
        val token = generation
        _state.value = _state.value.copy(sending = true, sendError = null)
        viewModelScope.launch {
            val sent = runCatching {
                gateway.sendText(VisionUserSession.mid, talkerId, trimmed, VisionUserSession.cookie, VisionUserSession.csrf)
            }.getOrDefault(false)
            if (token != generation) return@launch
            if (sent) {
                _state.value = _state.value.copy(
                    sending = false,
                    messages = _state.value.messages + CoreChatMessage(
                        senderUid = VisionUserSession.mid,
                        msgType = 1,
                        content = trimmed,
                        timestamp = System.currentTimeMillis() / 1000,
                        seqno = 0,
                    ),
                )
            } else {
                _state.value = _state.value.copy(sending = false, sendError = "发送失败，请重试")
            }
        }
    }

    private fun acknowledge() {
        val lastSeqno = _state.value.messages.maxOfOrNull { it.seqno } ?: 0L
        if (talkerId <= 0 || VisionUserSession.csrf.isBlank()) return
        viewModelScope.launch {
            runCatching { gateway.updateAck(talkerId, sessionType, lastSeqno, VisionUserSession.cookie, VisionUserSession.csrf) }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
