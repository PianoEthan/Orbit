package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.FollowApi
import com.qx.orbit.bili.data.model.FollowTag
import com.qx.orbit.bili.data.model.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowingListState(
    val users: List<UserInfo> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val tags: List<FollowTag> = emptyList(),
    val selectedTagId: Int? = null,
    val isLoadingTags: Boolean = false,
    val tagsError: String? = null
)

class FollowingListViewModel(
    private val loadTags: suspend () -> List<FollowTag> = FollowApi::getFollowTags,
    private val loadTagPage: suspend (Int, Int) -> List<UserInfo> = FollowApi::getFollowTagUsers,
    private val loadPage: suspend (Long, Int) -> FollowApi.FollowingPage = FollowApi::getFollowingList
) : ViewModel() {
    private val _state = MutableStateFlow(FollowingListState())
    val state = _state.asStateFlow()
    private var mid = 0L
    private var nextPage = 1
    private var canLoadTags = false
    private var usersJob: Job? = null
    private var tagsJob: Job? = null

    fun loadUser(mid: Long, isOwnList: Boolean = false) {
        if (this.mid == mid && canLoadTags == isOwnList) return
        usersJob?.cancel()
        tagsJob?.cancel()
        this.mid = mid
        canLoadTags = isOwnList
        nextPage = 1
        _state.value = FollowingListState()
        reloadTags()
        loadMore()
    }

    fun reloadTags() {
        if (!canLoadTags || _state.value.isLoadingTags) return
        _state.value = _state.value.copy(isLoadingTags = true, tagsError = null)
        tagsJob = viewModelScope.launch {
            try {
                val tags = loadTags().distinctBy { it.tagid }
                currentCoroutineContext().ensureActive()
                _state.value = _state.value.copy(tags = tags, isLoadingTags = false)
                val selected = _state.value.selectedTagId
                if (selected != null && tags.none { it.tagid == selected }) selectTag(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _state.value = _state.value.copy(isLoadingTags = false, tagsError = e.message ?: "加载关注分组失败")
            }
        }
    }

    fun selectTag(tagId: Int?) {
        if (_state.value.selectedTagId == tagId) return
        if (tagId != null && (!canLoadTags || _state.value.tags.none { it.tagid == tagId })) return
        usersJob?.cancel()
        nextPage = 1
        _state.value = _state.value.copy(
            users = emptyList(), selectedTagId = tagId, isLoading = false, hasMore = true, error = null
        )
        loadMore()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        nextPage = 1
        _state.value = _state.value.copy(hasMore = true)
        reloadTags()
        loadMore()
    }

    fun loadMore() {
        if (mid <= 0L || _state.value.isLoading || !_state.value.hasMore) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        val tagId = _state.value.selectedTagId
        val requestedPage = nextPage
        val requestedMid = mid
        usersJob = viewModelScope.launch {
            try {
                val page = if (tagId == null) {
                    loadPage(requestedMid, requestedPage)
                } else {
                    FollowApi.FollowingPage(loadTagPage(tagId, requestedPage))
                }
                currentCoroutineContext().ensureActive()
                val previous = if (requestedPage == 1) emptyList() else _state.value.users
                val users = (previous + page.users).distinctBy { it.mid }
                _state.value = _state.value.copy(
                    users = users,
                    isLoading = false,
                    hasMore = page.users.size == FollowApi.PAGE_SIZE &&
                        (page.total == null || users.size < page.total) && users.size > previous.size
                )
                nextPage = requestedPage + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "加载关注列表失败")
            }
        }
    }
}
