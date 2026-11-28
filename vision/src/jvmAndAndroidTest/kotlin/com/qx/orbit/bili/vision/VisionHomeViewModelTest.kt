package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreFeed
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.FeedGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun gateway(load: suspend (CoreFeed, Int) -> List<CoreVideo>) = object : FeedGateway {
        override suspend fun loadFeed(feed: CoreFeed, page: Int) = load(feed, page)
        override suspend fun removeVideo(video: CoreVideo) = Unit
    }

    private fun video(id: Long) = CoreVideo(id, "BV$id", "视频$id", "https://cover.test/$id", "UP")

    @Test fun sessionChangesClearOldFeedAndRestartPagination() = runTest(dispatcher) {
        val pages = mutableListOf<Int>()
        val model = VisionHomeViewModel(gateway { _, page -> pages += page; listOf(video(pages.size.toLong())) })
        advanceUntilIdle()
        model.onSessionChanged("")
        advanceUntilIdle()
        assertEquals(listOf(1), pages)
        model.loadMore()
        advanceUntilIdle()
        model.onSessionChanged("account-session")
        assertTrue(model.videos.value.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 1), pages)
        assertEquals(listOf(video(3)), model.videos.value)
        model.onSessionChanged("account-session")
        advanceUntilIdle()
        assertEquals(3, pages.size)
        model.onSessionChanged("")
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 1, 1), pages)
        assertEquals(2, model.feedRevision.value)
    }

    @Test fun independentSearchDoesNotRequestHomeFeedBeforeSubmitting() = runTest(dispatcher) {
        var feedCalls = 0
        val searches = mutableListOf<String>()
        val model = VisionHomeViewModel(
            gateway = gateway { _, _ -> feedCalls++; emptyList() },
            searchGateway = com.qx.orbit.bili.core.api.SearchGateway { keyword, _ -> searches += keyword; listOf(video(1)) },
            loadOnStart = false,
        )
        advanceUntilIdle()
        assertEquals(0, feedCalls)
        assertTrue(model.videos.value.isEmpty())
        model.search("BC")
        advanceUntilIdle()
        assertEquals(listOf("BC"), searches)
        assertEquals(0, feedCalls)
        assertEquals(listOf(video(1)), model.videos.value)
    }

    @Test fun switchingDuringLoadingIgnoresLateOldResponse() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<List<CoreVideo>>()
        val model = VisionHomeViewModel(gateway { feed, _ ->
            if (feed == CoreFeed.RECOMMEND) withContext(NonCancellable) { oldResponse.await() }
            else listOf(video(2))
        })
        runCurrent()
        model.selectFeed(CoreFeed.POPULAR)
        runCurrent()
        assertEquals(listOf(video(2)), model.videos.value)
        oldResponse.complete(listOf(video(1)))
        advanceUntilIdle()
        assertEquals(CoreFeed.POPULAR, model.feed.value)
        assertEquals(listOf(video(2)), model.videos.value)
        assertFalse(model.isLoading.value)
    }

    @Test fun failedPageRetriesSamePageAndKeepsExistingContent() = runTest(dispatcher) {
        val pages = mutableListOf<Int>()
        var fail = true
        val model = VisionHomeViewModel(gateway { _, page ->
            pages += page
            if (page == 2 && fail) {
                fail = false
                error("离线")
            }
            listOf(video(page.toLong()))
        })
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(video(1)), model.videos.value)
        assertEquals("离线", model.error.value)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(video(1), video(2)), model.videos.value)
        assertNull(model.error.value)
    }

    @Test fun repeatedLoadClicksAreCoalescedAndEmptyPageStopsPagination() = runTest(dispatcher) {
        val response = CompletableDeferred<List<CoreVideo>>()
        var calls = 0
        val model = VisionHomeViewModel(gateway { _, _ -> calls++; response.await() })
        model.loadMore()
        model.loadMore()
        runCurrent()
        assertEquals(1, calls)
        response.complete(emptyList())
        advanceUntilIdle()
        assertFalse(model.hasMore.value)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun refreshCancelsAppendWithoutLosingRefreshLoadingState() = runTest(dispatcher) {
        val append = CompletableDeferred<List<CoreVideo>>()
        val refresh = CompletableDeferred<List<CoreVideo>>()
        var first = true
        val model = VisionHomeViewModel(gateway { _, page ->
            if (first) { first = false; listOf(video(1)) }
            else if (page == 2) withContext(NonCancellable) { append.await() }
            else refresh.await()
        })
        advanceUntilIdle()
        model.loadMore()
        runCurrent()
        model.refresh()
        runCurrent()
        append.complete(listOf(video(2)))
        runCurrent()
        assertTrue(model.isLoading.value)
        refresh.complete(listOf(video(3)))
        advanceUntilIdle()
        assertEquals(listOf(video(3)), model.videos.value)
        assertFalse(model.isLoading.value)
    }

    @Test fun searchPaginationRetriesAndLeavingSearchUsesFeedAgain() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String, Int>>()
        var fail = true
        val model = VisionHomeViewModel(
            gateway = gateway { _, _ -> listOf(video(1)) },
            searchGateway = com.qx.orbit.bili.core.api.SearchGateway { keyword, page ->
                requests += keyword to page
                if (page == 2 && fail) { fail = false; error("离线") }
                listOf(video(10L + page))
            },
        )
        advanceUntilIdle()
        model.search("  测试  ")
        advanceUntilIdle()
        assertEquals("测试", model.query.value)
        assertEquals(listOf(video(11)), model.videos.value)
        model.loadMore()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("测试" to 1, "测试" to 2, "测试" to 2), requests)
        model.selectFeed(CoreFeed.RECOMMEND)
        advanceUntilIdle()
        assertNull(model.query.value)
        assertEquals(listOf(video(1)), model.videos.value)
    }

    @Test fun oldSearchCannotOverwriteNewKeywordOrFeed() = runTest(dispatcher) {
        val old = CompletableDeferred<List<CoreVideo>>()
        val model = VisionHomeViewModel(
            gateway = gateway { _, _ -> listOf(video(1)) },
            searchGateway = com.qx.orbit.bili.core.api.SearchGateway { keyword, _ ->
                if (keyword == "旧") withContext(NonCancellable) { old.await() } else listOf(video(3))
            },
        )
        advanceUntilIdle()
        model.search("旧")
        runCurrent()
        model.search("新")
        runCurrent()
        old.complete(listOf(video(2)))
        advanceUntilIdle()
        assertEquals("新", model.query.value)
        assertEquals(listOf(video(3)), model.videos.value)
        model.search(" ")
        assertEquals("新", model.query.value)
    }

    @Test fun emptyOrBlankBvidItemsAreFilteredToPreventDuplicateKeyCrash() = runTest(dispatcher) {
        val model = VisionHomeViewModel(gateway { _, _ ->
            listOf(
                video(1),
                CoreVideo(2, "", "直播卡片1", "https://img.test/2", "主播1"),
                CoreVideo(3, "   ", "直播卡片2", "https://img.test/3", "主播2"),
                video(4),
            )
        })
        advanceUntilIdle()
        assertEquals(listOf(video(1), video(4)), model.videos.value)
    }
}
