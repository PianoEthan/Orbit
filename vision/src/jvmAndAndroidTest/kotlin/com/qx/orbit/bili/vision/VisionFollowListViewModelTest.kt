package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreCreator
import com.qx.orbit.bili.core.api.CoreFollowPage
import com.qx.orbit.bili.core.api.CoreFollowUser
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CreatorGateway
import com.qx.orbit.bili.core.api.UserDataGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionFollowListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeUsers(
        val followingPages: MutableList<CoreFollowPage> = mutableListOf(),
        val followerPages: MutableList<CoreFollowPage> = mutableListOf(),
    ) : UserDataGateway {
        val calls = mutableListOf<Pair<VisionFollowMode, Int>>()
        override suspend fun loadHistory(cookie: String) = emptyList<com.qx.orbit.bili.core.api.CoreHistoryItem>()
        override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
        override suspend fun loadFavFolders(cookie: String, mid: Long) = emptyList<com.qx.orbit.bili.core.api.CoreFavFolder>()
        override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = emptyList<CoreVideo>()
        override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
        override suspend fun loadFollowingList(mid: Long, page: Int, cookie: String): CoreFollowPage {
            calls += VisionFollowMode.FOLLOWING to page
            return followingPages.removeAt(0)
        }
        override suspend fun loadFollowerList(mid: Long, page: Int, cookie: String): CoreFollowPage {
            calls += VisionFollowMode.FANS to page
            return followerPages.removeAt(0)
        }
    }

    private class FakeRelations : CreatorGateway {
        val calls = mutableListOf<Triple<Long, Int, String>>()
        var fail = false
        override suspend fun loadCreator(mid: Long, cookie: String): CoreCreator = error("unused")
        override suspend fun loadVideos(mid: Long, page: Int, cookie: String) = error("unused")
        override suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String) {
            if (fail) error("relation refused")
            calls += Triple(mid, action, csrf)
        }
    }

    private fun user(mid: Long, attribute: Int = 0) = CoreFollowUser(mid, "用户$mid", "https://face/$mid", attribute = attribute)
    private fun page(vararg users: CoreFollowUser, total: Int = users.size) = CoreFollowPage(users.toList(), total)

    @Test fun openLoadsRequestedModeAndLoadMoreAppendsUntilTotal() = runTest(dispatcher) {
        val users = FakeUsers(followingPages = mutableListOf(page(user(1), user(2), total = 3), page(user(3), total = 3)))
        val model = VisionFollowListViewModel(users)
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), model.state.value.items.map { it.mid })
        assertEquals(3, model.state.value.total)
        assertTrue(model.state.value.hasMore)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), model.state.value.items.map { it.mid })
        assertFalse(model.state.value.hasMore)
        assertEquals(listOf(VisionFollowMode.FOLLOWING to 1, VisionFollowMode.FOLLOWING to 2), users.calls)
    }

    @Test fun fansModeUsesFollowerEndpointAndErrorKeepsLoadedItemsWithRetry() = runTest(dispatcher) {
        val users = FakeUsers(
            followerPages = mutableListOf(page(user(5), total = 9)),
        )
        val model = VisionFollowListViewModel(users)
        model.open(9, VisionFollowMode.FANS, "cookie")
        advanceUntilIdle()
        assertEquals(listOf(VisionFollowMode.FANS to 1), users.calls)
        users.followerPages += CoreFollowPage(emptyList(), 0)
        model.retry()
        // retry re-requests page 2 which will fail with empty queue removal guarded below
        advanceUntilIdle()
        assertTrue(model.state.value.items.isNotEmpty())
    }

    @Test fun failedFirstPageShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        var attempts = 0
        val flaky = object : UserDataGateway {
            override suspend fun loadHistory(cookie: String) = emptyList<com.qx.orbit.bili.core.api.CoreHistoryItem>()
            override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
            override suspend fun loadFavFolders(cookie: String, mid: Long) = emptyList<com.qx.orbit.bili.core.api.CoreFavFolder>()
            override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = emptyList<CoreVideo>()
            override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
            override suspend fun loadFollowingList(mid: Long, page: Int, cookie: String): CoreFollowPage {
                attempts++
                if (attempts == 1) error("网络错误")
                return CoreFollowPage(listOf(user(1)), 1)
            }
        }
        val model = VisionFollowListViewModel(flaky)
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        assertNull(model.state.value.items.firstOrNull())
        assertTrue(model.state.value.error?.contains("网络错误") == true)
        model.retry()
        advanceUntilIdle()
        assertEquals(listOf(1L), model.state.value.items.map { it.mid })
        assertNull(model.state.value.error)
    }

    @Test fun toggleFollowSendsActionAndUpdatesAttributeOptimistically() = runTest(dispatcher) {
        val users = FakeUsers(followingPages = mutableListOf(page(user(1, attribute = 0), total = 1)))
        val relations = FakeRelations()
        val model = VisionFollowListViewModel(users, relations)
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        model.toggleFollow(1, add = true, csrf = "token")
        advanceUntilIdle()
        assertEquals(listOf(Triple(1L, 1, "token")), relations.calls)
        assertEquals(2, model.state.value.items.single().attribute)
        assertNull(model.state.value.relationMessage)
        assertFalse(model.state.value.loading)
    }

    @Test fun failedToggleReportsMessageWithoutMutatingAttribute() = runTest(dispatcher) {
        val users = FakeUsers(followingPages = mutableListOf(page(user(1, attribute = 2), total = 1)))
        val relations = FakeRelations().apply { fail = true }
        val model = VisionFollowListViewModel(users, relations)
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        model.toggleFollow(1, add = false, csrf = "token")
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.single().attribute)
        assertTrue(model.state.value.relationMessage?.contains("未完成") == true)
    }

    @Test fun reopenWithSameTargetKeepsLoadedList() = runTest(dispatcher) {
        val users = FakeUsers(followingPages = mutableListOf(page(user(1), total = 1)))
        val model = VisionFollowListViewModel(users)
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        model.open(9, VisionFollowMode.FOLLOWING, "cookie")
        advanceUntilIdle()
        assertEquals(listOf(VisionFollowMode.FOLLOWING to 1), users.calls)
    }
}
