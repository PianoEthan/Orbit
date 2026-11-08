package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionUserCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun video(id: Long) = CoreVideo(id, "BV$id", "视频$id", "", "UP")
    private fun gateway(load: suspend (Long, Int) -> CoreFavVideoPage) = object : UserDataGateway {
        override suspend fun loadHistory(cookie: String) = emptyList<CoreHistoryItem>()
        override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
        override suspend fun loadFavFolders(cookie: String, mid: Long) = listOf(CoreFavFolder(1, "A", 50, ""), CoreFavFolder(2, "B", 1, ""))
        override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = load(folderId, page).videos
        override suspend fun loadFavVideoPage(cookie: String, folderId: Long, page: Int) = load(folderId, page)
        override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
    }
    private fun model(gateway: UserDataGateway, cookie: () -> String = { "test-cookie" }) = VisionUserCenterViewModel(
        userGateway = gateway, cookieProvider = cookie, midProvider = { 123L }, onProfile = {},
    )

    @Test fun removingWatchLaterKeepsItemOnFailureAndRemovesAfterSuccess() = runTest(dispatcher) {
        var fail = true
        var calls = 0
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadWatchLater(cookie: String) = listOf(video(1), video(2))
            override suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String) {
                calls++
                assertFalse(add)
                if (fail) error("offline")
            }
        }
        val model = model(testGateway, cookie = { "SESSDATA=test; bili_jct=csrf" })
        model.selectTab(UserCenterTab.WATCH_LATER); advanceUntilIdle()
        model.removeWatchLater(video(1)); model.removeWatchLater(video(1)); advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf(video(1), video(2)), model.watchLater.value)
        assertEquals("移除未确认，请刷新后再操作", model.mutationMessage.value)
        fail = false
        model.removeWatchLater(video(1)); advanceUntilIdle()
        assertEquals(listOf(video(2)), model.watchLater.value)
        assertEquals("已从稍后再看移除", model.mutationMessage.value)
        assertNull(model.removing.value)
    }

    @Test fun lateRemovalAfterLogoutCannotWriteSuccessIntoNewSession() = runTest(dispatcher) {
        var cookie = "SESSDATA=test; bili_jct=csrf"
        val response = CompletableDeferred<Unit>()
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadWatchLater(cookie: String) = listOf(video(1))
            override suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String) = withContext(NonCancellable) { response.await() }
        }
        val model = model(testGateway, cookie = { cookie })
        model.selectTab(UserCenterTab.WATCH_LATER); advanceUntilIdle()
        model.removeWatchLater(video(1)); runCurrent()
        cookie = ""
        model.onSessionChanged()
        response.complete(Unit); advanceUntilIdle()
        assertTrue(model.watchLater.value.isEmpty())
        assertNull(model.mutationMessage.value)
        assertNull(model.removing.value)
    }

    @Test fun dynamicRetriesSameOffsetAndDeduplicatesVideos() = runTest(dispatcher) {
        var fail = true
        val offsets = mutableListOf<String>()
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadDynamicVideoPage(cookie: String, offset: String): CoreDynamicVideoPage {
                offsets += offset
                if (offset.isEmpty()) return CoreDynamicVideoPage(listOf(video(1)), "next")
                if (fail) error("offline")
                return CoreDynamicVideoPage(listOf(video(1), video(2)), null)
            }
        }
        val model = model(testGateway)
        model.selectTab(UserCenterTab.DYNAMIC); advanceUntilIdle()
        model.loadMoreDynamic(); model.loadMoreDynamic(); advanceUntilIdle()
        assertEquals(listOf(video(1)), model.dynamicVideos.value)
        assertNotNull(model.error.value)
        fail = false
        model.retry(); advanceUntilIdle()
        assertEquals(listOf("", "next", "next"), offsets)
        assertEquals(listOf(video(1), video(2)), model.dynamicVideos.value)
        assertFalse(model.dynamicHasMore.value)
    }

    @Test fun dynamicLateResponseCannotRestoreLoggedOutData() = runTest(dispatcher) {
        var cookie = "account"
        val old = CompletableDeferred<CoreDynamicVideoPage>()
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadDynamicVideoPage(cookie: String, offset: String) = withContext(NonCancellable) { old.await() }
        }
        val model = model(testGateway, cookie = { cookie })
        model.selectTab(UserCenterTab.DYNAMIC); runCurrent()
        cookie = ""; model.onSessionChanged()
        old.complete(CoreDynamicVideoPage(listOf(video(1)), "next")); advanceUntilIdle()
        assertTrue(model.dynamicVideos.value.isEmpty())
        assertFalse(model.dynamicHasMore.value)
    }

    @Test fun historyRetriesCursorAndKeepsDifferentPartsOfSameVideo() = runTest(dispatcher) {
        var fail = true
        val cursor = CoreHistoryCursor(55, 1234, "archive")
        val calls = mutableListOf<CoreHistoryCursor>()
        val first = CoreHistoryItem(1, "BV1", 11, "视频", "", "UP", 0, 100, 0f)
        val second = first.copy(cid = 22)
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadHistoryPage(cookie: String, cursor: CoreHistoryCursor): CoreHistoryPage {
                calls += cursor
                if (cursor.max == 0L) return CoreHistoryPage(listOf(first), CoreHistoryCursor(55, 1234, "archive"))
                if (fail) error("offline")
                return CoreHistoryPage(listOf(first, second), null)
            }
        }
        val model = model(testGateway)
        model.retry(); advanceUntilIdle()
        model.loadMoreHistory(); model.loadMoreHistory(); advanceUntilIdle()
        assertEquals(listOf(first), model.history.value)
        fail = false
        model.retry(); advanceUntilIdle()
        assertEquals(listOf(CoreHistoryCursor(), cursor, cursor), calls)
        assertEquals(listOf(first, second), model.history.value)
        assertFalse(model.historyHasMore.value)
    }

    @Test fun historyLatePageCannotReturnAfterLogout() = runTest(dispatcher) {
        var cookie = "account"
        val result = CompletableDeferred<CoreHistoryPage>()
        val item = CoreHistoryItem(1, "BV1", 11, "视频", "", "UP", 0, 100, 0f)
        val base = gateway { _, _ -> CoreFavVideoPage(emptyList(), false) }
        val testGateway = object : UserDataGateway by base {
            override suspend fun loadHistoryPage(cookie: String, cursor: CoreHistoryCursor) = withContext(NonCancellable) { result.await() }
        }
        val model = model(testGateway, cookie = { cookie })
        model.retry(); runCurrent()
        cookie = ""; model.onSessionChanged()
        result.complete(CoreHistoryPage(listOf(item), CoreHistoryCursor(55, 1))); advanceUntilIdle()
        assertTrue(model.history.value.isEmpty())
        assertFalse(model.historyHasMore.value)
    }

    @Test fun pagingRetriesSamePageAndKeepsVideos() = runTest(dispatcher) {
        var fail = true
        val pages = mutableListOf<Int>()
        val model = model(gateway { _, page ->
            pages += page
            if (page == 2 && fail) error("offline")
            CoreFavVideoPage(listOf(video(page.toLong())), page < 2)
        })
        model.selectTab(UserCenterTab.FAVORITES); advanceUntilIdle()
        assertEquals(1L, model.selectedFolder.value)
        model.loadMoreFavorites(); advanceUntilIdle()
        assertEquals(listOf(video(1)), model.favVideos.value)
        assertNotNull(model.error.value)
        fail = false
        model.retry(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(video(1), video(2)), model.favVideos.value)
        assertFalse(model.favHasMore.value)
    }

    @Test fun switchingFolderIgnoresLateResponseAndResetsPaging() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreFavVideoPage>()
        val model = model(gateway { folder, _ ->
            if (folder == 1L) withContext(NonCancellable) { old.await() }
            else CoreFavVideoPage(listOf(video(2)), false)
        })
        model.selectTab(UserCenterTab.FAVORITES); runCurrent()
        model.selectFolder(2); runCurrent()
        old.complete(CoreFavVideoPage(listOf(video(1)), true)); advanceUntilIdle()
        assertEquals(2L, model.selectedFolder.value)
        assertEquals(listOf(video(2)), model.favVideos.value)
        assertFalse(model.isLoading.value)
    }

    @Test fun leavingFavoritesIgnoresOldErrorAndLoadingState() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreFavVideoPage>()
        val model = model(gateway { _, _ -> withContext(NonCancellable) { old.await() } })
        model.selectTab(UserCenterTab.FAVORITES); runCurrent()
        model.selectTab(UserCenterTab.WATCH_LATER); runCurrent()
        old.completeExceptionally(IllegalStateException("old failure")); advanceUntilIdle()
        assertEquals(UserCenterTab.WATCH_LATER, model.tab.value)
        assertNull(model.error.value)
        assertFalse(model.isLoading.value)
        assertTrue(model.favVideos.value.isEmpty())
    }

    @Test fun repeatedPageDoesNotDuplicateItemsOrContinueForever() = runTest(dispatcher) {
        var calls = 0
        val model = model(gateway { _, _ -> calls++; CoreFavVideoPage(listOf(video(1)), true) })
        model.selectTab(UserCenterTab.FAVORITES); advanceUntilIdle()
        model.loadMoreFavorites(); model.loadMoreFavorites(); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(listOf(video(1)), model.favVideos.value)
        assertFalse(model.favHasMore.value)
    }

    @Test fun changedSessionCannotReceiveOldAccountResults() = runTest(dispatcher) {
        var cookie = "account-a"
        val old = CompletableDeferred<CoreFavVideoPage>()
        val model = model(gateway { _, _ -> withContext(NonCancellable) { old.await() } }, cookie = { cookie })
        model.selectTab(UserCenterTab.FAVORITES); runCurrent()
        cookie = ""
        model.onSessionChanged()
        old.complete(CoreFavVideoPage(listOf(video(1)), true)); advanceUntilIdle()
        assertTrue(model.favVideos.value.isEmpty())
        assertTrue(model.favFolders.value.isEmpty())
        assertNull(model.selectedFolder.value)
        assertFalse(model.isLoading.value)
    }
}
