package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliCreatorGateway
import com.qx.orbit.bili.core.api.BilibiliUserDataGateway
import com.qx.orbit.bili.core.api.CoreFollowUser
import com.qx.orbit.bili.core.api.CreatorGateway
import com.qx.orbit.bili.core.api.UserDataGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisionFollowMode { FOLLOWING, FANS }

data class VisionFollowListState(
    val items: List<CoreFollowUser> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val relationBusy: Long = 0L,
    val relationMessage: String? = null,
)

class VisionFollowListViewModel(
    private val gateway: UserDataGateway = BilibiliUserDataGateway(),
    private val relations: CreatorGateway = BilibiliCreatorGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionFollowListState())
    val state = _state.asStateFlow()

    private var mid = 0L
    private var mode = VisionFollowMode.FOLLOWING
    private var cookie = ""
    private var page = 1
    private var generation = 0
    private var job: Job? = null
    private var relationJob: Job? = null

    fun open(mid: Long, mode: VisionFollowMode, cookie: String) {
        if (mid == this.mid && mode == this.mode && cookie == this.cookie && _state.value.items.isNotEmpty()) return
        generation++
        job?.cancel()
        this.mid = mid
        this.mode = mode
        this.cookie = cookie
        page = 1
        _state.value = VisionFollowListState()
        loadPage()
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        loadPage()
    }

    fun retry() {
        if (_state.value.loading) return
        loadPage()
    }

    fun toggleFollow(targetMid: Long, add: Boolean, csrf: String) {
        if (targetMid <= 0 || cookie.isBlank() || csrf.isBlank() || _state.value.relationBusy != 0L) return
        val token = generation
        _state.value = _state.value.copy(relationBusy = targetMid, relationMessage = null)
        relationJob = viewModelScope.launch {
            val result = runCatching { relations.changeRelation(targetMid, if (add) 1 else 2, cookie, csrf) }
            if (token != generation) return@launch
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    relationBusy = 0L,
                    items = _state.value.items.map { user ->
                        if (user.mid == targetMid) user.copy(attribute = if (add) 2 else 0) else user
                    },
                )
            } else {
                _state.value = _state.value.copy(relationBusy = 0L, relationMessage = "操作未完成，请刷新后重试")
            }
        }
    }

    private fun loadPage() {
        if (mid <= 0) return
        val token = generation
        val requestedPage = page
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val pageResult = if (mode == VisionFollowMode.FOLLOWING) {
                    gateway.loadFollowingList(mid, requestedPage, cookie)
                } else {
                    gateway.loadFollowerList(mid, requestedPage, cookie)
                }
                if (token == generation) {
                    val combined = (_state.value.items + pageResult.items).distinctBy { it.mid }
                    _state.value = _state.value.copy(
                        items = combined,
                        total = pageResult.total,
                        loading = false,
                        hasMore = pageResult.items.isNotEmpty() && combined.size < pageResult.total,
                    )
                    page = requestedPage + 1
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == generation) {
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "列表加载失败")
                }
            }
        }
    }
}
