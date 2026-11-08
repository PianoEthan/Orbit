package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.DynamicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RepostState(
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null
)

class RepostDynamicViewModel internal constructor(
    private val savedState: SavedStateHandle,
    private val repost: suspend (String, String) -> Unit
) : ViewModel() {
    constructor(savedState: SavedStateHandle) : this(savedState, DynamicApi::repostDynamic)

    val text = savedState.getStateFlow("text", "")
    private val _state = MutableStateFlow(RepostState())
    val state = _state.asStateFlow()

    fun edit(text: String) {
        savedState["text"] = text
    }

    fun send(dynamicId: String) {
        if (_state.value.isSending || _state.value.sent) return
        _state.value = RepostState(isSending = true)
        val content = text.value
        viewModelScope.launch {
            try {
                repost(dynamicId, content)
                _state.value = RepostState(sent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RepostState(error = e.message ?: "转发失败，请重试")
            }
        }
    }

    fun reset() {
        savedState["text"] = ""
        _state.value = RepostState()
    }
}
