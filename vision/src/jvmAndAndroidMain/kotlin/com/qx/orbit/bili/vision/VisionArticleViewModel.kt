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
    val busy: Boolean = false,
    val message: String? = null,
    val liked: Boolean = false,
    val favorited: Boolean = false,
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

    fun like(cv: Long, cookie: String, csrf: String) {
        val target = !_state.value.liked
        interact("点赞成功", { gateway.likeArticle(cv, target, cookie, csrf) }) { ok -> if (ok) _state.value.copy(liked = target) else _state.value }
    }

    fun favorite(cv: Long, cookie: String, csrf: String) {
        val target = !_state.value.favorited
        interact("收藏成功", { gateway.favoriteArticle(cv, target, cookie, csrf) }) { ok -> if (ok) _state.value.copy(favorited = target) else _state.value }
    }

    fun coin(cv: Long, upid: Long, cookie: String, csrf: String) {
        interact("投币成功", { gateway.coinArticle(cv, upid, 1, cookie, csrf) }) { _state.value }
    }

    private fun interact(successMessage: String, block: suspend () -> Boolean, transform: (Boolean) -> VisionArticleState) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val ok = block()
                _state.value = transform(ok).copy(busy = false, message = if (ok) successMessage else "操作失败，请重试")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(busy = false, message = "操作失败，请重试")
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }
}
