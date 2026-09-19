package com.qx.orbit.bili.presentation.viewmodel

import com.qx.orbit.bili.data.api.FollowApi
import com.qx.orbit.bili.data.model.FollowTag
import com.qx.orbit.bili.data.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowingGroupsTest {
    private val dispatcher = StandardTestDispatcher()
    private val tags = listOf(FollowTag(0, "默认分组", 1), FollowTag(7, "创作者", 21))

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun defaultGroupIsDistinctFromAllAndEachGroupStartsOnPageOne() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<Int, Int>>()
        val model = FollowingListViewModel(
            loadTags = { tags },
            loadTagPage = { tag, page ->
                requests += tag to page
                when {
                    tag == 0 -> listOf(UserInfo(mid = 2))
                    page == 1 -> (100L..119L).map { UserInfo(mid = it) }
                    else -> listOf(UserInfo(mid = 120))
                }
            },
            loadPage = { _, page ->
                assertEquals(1, page)
                FollowApi.FollowingPage(listOf(UserInfo(mid = 1)), 1)
            }
        )
        model.loadUser(10, isOwnList = true)
        advanceUntilIdle()
        assertEquals(tags, model.state.value.tags)
        model.selectTag(0)
        advanceUntilIdle()
        assertEquals(0, model.state.value.selectedTagId)
        assertEquals(listOf(2L), model.state.value.users.map { it.mid })
        model.selectTag(7)
        advanceUntilIdle()
        assertEquals(20, model.state.value.users.size)
        assertTrue(model.state.value.hasMore)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(21, model.state.value.users.size)
        assertFalse(model.state.value.hasMore)
        assertEquals(listOf(0 to 1, 7 to 1, 7 to 2), requests)
        model.selectTag(null)
        advanceUntilIdle()
        assertNull(model.state.value.selectedTagId)
        assertEquals(listOf(1L), model.state.value.users.map { it.mid })
    }

    @Test
    fun switchingGroupsIgnoresLateResultsFromThePreviousGroup() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<List<UserInfo>>()
        val model = FollowingListViewModel(
            loadTags = { tags },
            loadTagPage = { tag, _ ->
                if (tag == 0) withContext(NonCancellable) { oldResponse.await() }
                else listOf(UserInfo(mid = 7))
            },
            loadPage = { _, _ -> FollowApi.FollowingPage(emptyList(), 0) }
        )
        model.loadUser(10, isOwnList = true)
        advanceUntilIdle()
        model.selectTag(0)
        runCurrent()
        model.selectTag(7)
        runCurrent()
        oldResponse.complete(listOf(UserInfo(mid = 99)))
        advanceUntilIdle()
        assertEquals(7, model.state.value.selectedTagId)
        assertEquals(listOf(7L), model.state.value.users.map { it.mid })
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun groupAndMemberFailuresCanBeRetriedIndependently() = runTest(dispatcher) {
        var failTags = true
        var failUsers = true
        val requestedPages = mutableListOf<Int>()
        val model = FollowingListViewModel(
            loadTags = { if (failTags) error("分组加载失败") else tags },
            loadTagPage = { _, page ->
                requestedPages += page
                if (failUsers) error("组内用户加载失败")
                emptyList()
            },
            loadPage = { _, _ -> FollowApi.FollowingPage(listOf(UserInfo(mid = 1)), 1) }
        )
        model.loadUser(10, isOwnList = true)
        advanceUntilIdle()
        assertEquals("分组加载失败", model.state.value.tagsError)
        assertEquals(1, model.state.value.users.size)
        failTags = false
        model.reloadTags()
        advanceUntilIdle()
        assertNull(model.state.value.tagsError)
        model.selectTag(0)
        advanceUntilIdle()
        assertEquals("组内用户加载失败", model.state.value.error)
        assertEquals(tags, model.state.value.tags)
        failUsers = false
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 1), requestedPages)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.hasMore)
    }

    @Test
    fun viewingAnotherUserDoesNotLoadTheCurrentAccountsGroups() = runTest(dispatcher) {
        val model = FollowingListViewModel(
            loadTags = { error("不应请求当前账号分组") },
            loadTagPage = { _, _ -> error("不应请求当前账号组内用户") },
            loadPage = { mid, _ -> FollowApi.FollowingPage(listOf(UserInfo(mid = mid)), 1) }
        )
        model.loadUser(200)
        advanceUntilIdle()
        model.selectTag(0)
        advanceUntilIdle()
        assertNull(model.state.value.selectedTagId)
        assertNull(model.state.value.tagsError)
        assertTrue(model.state.value.tags.isEmpty())
        assertEquals(listOf(200L), model.state.value.users.map { it.mid })
    }
}
