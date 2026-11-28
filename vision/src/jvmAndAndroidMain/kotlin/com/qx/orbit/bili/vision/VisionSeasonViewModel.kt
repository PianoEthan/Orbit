package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliSeasonGateway
import com.qx.orbit.bili.core.api.CoreSeason
import com.qx.orbit.bili.core.api.CoreSeasonDetail
import com.qx.orbit.bili.core.api.SeasonGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisionSeasonState(
    val items: List<CoreSeason> = emptyList(), val detail: CoreSeasonDetail? = null,
    val loading: Boolean = false, val hasMore: Boolean = false, val error: String? = null,
    val pending: Boolean = false, val message: String? = null,
)

class VisionSeasonViewModel(private val gateway: SeasonGateway = BilibiliSeasonGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionSeasonState())
    val state = _state.asStateFlow()
    private var type = 1
    private var followed = false
    private var mid = 0L
    private var seasonId = 0L
    private var cookie = ""
    private var page = 1
    private var generation = 0
    private var job: Job? = null
    private var requestKey = ""

    fun openCatalog(type: Int, followed: Boolean, mid: Long, cookie: String) {
        val key = "$type:$followed:$mid:$cookie"
        if (requestKey == key && seasonId == 0L) return
        requestKey = key
        this.type = type
        this.followed = followed
        this.mid = mid
        this.cookie = cookie
        seasonId = 0L
        reset()
        loadMore()
    }

    fun openDetail(id: Long, cookie: String, force: Boolean = false) {
        if (!force && seasonId == id && this.cookie == cookie && (_state.value.loading || _state.value.detail != null)) return
        seasonId = id
        this.cookie = cookie
        generation++
        job?.cancel()
        val token = generation
        val account = cookie
        val sameAccount = requestKey == "detail:$id:$account"
        requestKey = "detail:$id:$account"
        _state.value = _state.value.copy(loading = true, error = null, pending = false, message = null,
            detail = if (sameAccount) _state.value.detail?.takeIf { it.season.id == id } else null)
        job = viewModelScope.launch {
            try {
                val detail = gateway.loadSeason(id, account)
                if (token == generation) _state.value = _state.value.copy(detail = detail, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "剧集加载失败")
            }
        }
    }

    private fun reset() {
        generation++
        job?.cancel()
        page = 1
        _state.value = VisionSeasonState(hasMore = true)
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore || seasonId != 0L) return
        val token = generation
        val requestedPage = page
        val account = cookie
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val result = if (followed) gateway.loadFollowed(mid, type, requestedPage, account) else gateway.loadCatalog(type, requestedPage, account)
                if (token == generation) {
                    _state.value = _state.value.copy(items = (_state.value.items + result.items).distinctBy { it.id }, loading = false, hasMore = result.hasMore)
                    page = requestedPage + 1
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "剧集列表加载失败")
            }
        }
    }

    fun retry() {
        if (seasonId > 0) openDetail(seasonId, cookie, force = true) else loadMore()
    }

    fun changeFollow(csrf: String) {
        val detail = _state.value.detail ?: return
        if (_state.value.pending || _state.value.loading || _state.value.error != null || cookie.isBlank() || csrf.isBlank()) return
        val token = generation
        val account = cookie
        val target = !detail.followed
        _state.value = _state.value.copy(pending = true, message = null)
        viewModelScope.launch {
            try {
                gateway.follow(detail.season.id, target, account, csrf)
                if (token == generation) _state.value = _state.value.copy(pending = false, detail = detail.copy(followed = target), message = if (target) "已追剧" else "已取消追剧")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (token == generation) _state.value = _state.value.copy(pending = false, message = "操作结果未确认，请刷新后重试")
            }
        }
    }
}
