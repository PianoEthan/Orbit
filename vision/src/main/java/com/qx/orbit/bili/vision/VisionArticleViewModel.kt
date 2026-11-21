package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.ArticleGateway
import com.qx.orbit.bili.core.api.BilibiliArticleGateway
import com.qx.orbit.bili.core.api.CoreArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class VisionArticleState(
    val article: CoreArticle? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

internal class VisionArticleViewModel(private val gateway: ArticleGateway = BilibiliArticleGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionArticleState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun open(id: String, isOpus: Boolean, cookie: String) {
        job?.cancel()
        _state.value = VisionArticleState(loading = true)
        job = viewModelScope.launch {
            try {
                val article = if (isOpus) gateway.loadOpus(id, cookie) else gateway.loadArticle(id.toLongOrNull() ?: 0L, cookie)
                _state.value = VisionArticleState(article = article)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = VisionArticleState(error = "内容加载失败，请重试")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }
}
