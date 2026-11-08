package com.qx.orbit.bili.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.qx.orbit.bili.data.api.FollowApi
import com.qx.orbit.bili.data.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowingAndRepostTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun followingRetriesTheFailedPageWithoutDroppingLoadedUsers() = runTest(dispatcher) {
        val requested = mutableListOf<Int>()
        var fail = true
        val model = FollowingListViewModel { _, page ->
            requested += page
            if (page == 2 && fail) error("网络错误")
            FollowApi.FollowingPage(if (page == 1) users(1, 20) else users(20, 21), 21)
        }
        model.loadUser(100)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1), requested)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(20, model.state.value.users.size)
        assertEquals("网络错误", model.state.value.error)
        fail = false
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), requested)
        assertEquals(21, model.state.value.users.size)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.hasMore)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(3, requested.size)
    }

    @Test
    fun followingStopsForEmptyListsAndCanRefreshThem() = runTest(dispatcher) {
        var calls = 0
        val model = FollowingListViewModel { _, page ->
            calls++
            assertEquals(1, page)
            FollowApi.FollowingPage(if (calls == 1) emptyList() else users(9, 9), calls - 1)
        }
        model.loadUser(100)
        advanceUntilIdle()
        assertFalse(model.state.value.hasMore)
        assertNull(model.state.value.error)
        model.refresh()
        advanceUntilIdle()
        assertEquals(listOf(9L), model.state.value.users.map { it.mid })
    }

    @Test
    fun followingShowsPrivacyErrorsInsteadOfAnEmptySuccess() = runTest(dispatcher) {
        val model = FollowingListViewModel { _, _ -> error("用户设置隐私，无法查看") }
        model.loadUser(100)
        advanceUntilIdle()
        assertEquals("用户设置隐私，无法查看", model.state.value.error)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun repostKeepsDraftOnFailureAndAllowsRetry() = runTest(dispatcher) {
        var calls = 0
        val saved = SavedStateHandle()
        val model = RepostDynamicViewModel(saved) { id, text ->
            assertEquals("1234567890123456789", id)
            assertEquals("附言\n带有\"引号\"", text)
            if (++calls == 1) error("转发失败")
        }
        model.edit("附言\n带有\"引号\"")
        model.send("1234567890123456789")
        advanceUntilIdle()
        assertEquals("转发失败", model.state.value.error)
        assertEquals("附言\n带有\"引号\"", model.text.value)
        assertEquals(model.text.value, saved.get<String>("text"))
        assertFalse(model.state.value.sent)
        model.send("1234567890123456789")
        advanceUntilIdle()
        assertTrue(model.state.value.sent)
        assertNull(model.state.value.error)
        model.reset()
        assertEquals("", model.text.value)
    }

    @Test
    fun repostSendsOnlyOnceWhilePendingAndUntilSuccessIsConsumed() = runTest(dispatcher) {
        var calls = 0
        val response = CompletableDeferred<Unit>()
        val model = RepostDynamicViewModel(SavedStateHandle()) { _, _ ->
            calls++
            response.await()
        }
        model.send("123")
        model.send("123")
        runCurrent()
        assertTrue(model.state.value.isSending)
        assertEquals(1, calls)
        response.complete(Unit)
        advanceUntilIdle()
        model.send("123")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(model.state.value.sent)
    }

    private fun users(first: Int, last: Int) = (first..last).map { UserInfo(mid = it.toLong()) }
}
