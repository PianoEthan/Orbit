package com.qx.orbit.bili.vision

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliVideoGateway
import com.qx.orbit.bili.core.api.CoreReplyRejectedException
import com.qx.orbit.bili.core.api.VideoGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionReplyComposerState(
    val confirming: Boolean = false,
    val sending: Boolean = false,
    val uncertain: Boolean = false,
    val sentId: Long? = null,
    val message: String? = null,
)

internal class VisionReplyComposerViewModel @JvmOverloads constructor(
    private val saved: SavedStateHandle,
    private val gateway: VideoGateway = BilibiliVideoGateway(),
    private val cookieProvider: () -> String = { VisionUserSession.cookie },
) : ViewModel() {
    val draft = saved.getStateFlow("draft", "")
    private val _state = MutableStateFlow(VisionReplyComposerState(
        uncertain = saved.get<Boolean>("pending") == true,
        sentId = saved["sentId"],
        message = if (saved.get<Boolean>("pending") == true) "上次发送结果未确认，请先返回评论区核实" else null,
    ))
    val state = _state.asStateFlow()
    private var cookie = ""
    private var generation = 0
    private var job: Job? = null

    fun setAccount(session: String) {
        if (cookie == session) return
        stop()
        cookie = session
    }

    fun edit(value: String) {
        if (_state.value.sending || _state.value.sentId != null) return
        saved["draft"] = value
        _state.value = _state.value.copy(confirming = false)
    }

    fun requestConfirmation() {
        if (_state.value.sending || _state.value.sentId != null || _state.value.uncertain) return
        val message = when {
            cookie.isBlank() -> "请先登录后发表评论"
            draft.value.isBlank() || draft.value.codePointCount(0, draft.value.length) > 1000 -> "评论需为 1–1000 字"
            else -> null
        }
        _state.value = _state.value.copy(confirming = message == null, message = message)
    }

    fun dismissConfirmation() { _state.value = _state.value.copy(confirming = false) }

    fun acknowledgeUncertain() {
        if (!_state.value.sending) {
            saved["pending"] = false
            _state.value = _state.value.copy(uncertain = false, message = "再次发送可能产生重复评论，请确认后操作")
        }
    }

    fun send(aid: Long, root: Long, parent: Long, csrf: String) {
        if (!_state.value.confirming || _state.value.sending || _state.value.sentId != null || _state.value.uncertain) return
        if (cookie.isBlank() || cookieProvider() != cookie || csrf.isBlank() || aid <= 0) {
            _state.value = _state.value.copy(confirming = false, message = "登录状态已变化，请重新登录后操作")
            return
        }
        val session = cookie
        val text = draft.value
        val request = ++generation
        saved["pending"] = true
        _state.value = VisionReplyComposerState(sending = true)
        job = viewModelScope.launch {
            try {
                val id = gateway.sendReply(aid, root, parent, text, session, csrf)
                if (request != generation || cookieProvider() != session) return@launch
                saved["pending"] = false
                saved["sentId"] = id
                saved["draft"] = ""
                _state.value = VisionReplyComposerState(sentId = id, message = "评论已提交，展示时间以评论区审核为准")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request != generation || cookieProvider() != session) return@launch
                val rejected = error is CoreReplyRejectedException
                saved["pending"] = !rejected
                _state.value = VisionReplyComposerState(uncertain = !rejected,
                    message = if (rejected) error.message else "发送结果未确认，草稿已保留。请先返回评论区核实，不会自动重发")
            }
        }
    }

    fun clearSentResult() {
        if (_state.value.sentId != null) {
            saved["sentId"] = null
            _state.value = VisionReplyComposerState()
        }
    }

    fun stop() {
        generation++
        job?.cancel()
        val uncertain = _state.value.uncertain || _state.value.sending
        _state.value = _state.value.copy(confirming = false, sending = false, uncertain = uncertain,
            message = if (_state.value.sending) "发送已中断，结果未确认，请先返回评论区核实" else _state.value.message)
    }
}
