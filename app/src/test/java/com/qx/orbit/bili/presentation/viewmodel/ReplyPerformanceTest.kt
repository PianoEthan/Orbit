package com.qx.orbit.bili.presentation.viewmodel

import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.VideoInfo
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
import com.qx.orbit.bili.data.model.Bangumi
import com.qx.orbit.bili.data.model.History
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyPerformanceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun returningToSameVideoKeepsLoadedCommentPagesAndRefreshIsExplicit() = runTest(dispatcher) {
        var infoRequests = 0
        val pages = mutableListOf<Int>()
        val model = VideoDetailViewModel(
            loadVideoInfo = { infoRequests++; VideoInfo(bvid = it, aid = 10) },
            loadTags = { "标签" },
            loadRelated = { emptyList() },
            loadReplyPage = { _, page -> pages += page; ReplyApi.ReplyPage(listOf(Reply(rpid = page.toLong())), page < 3) },
            loadReplyCount = { 3L }
        )
        model.loadData("BV1", 10)
        model.loadData("BV1", 10)
        advanceUntilIdle()
        model.loadReplies()
        advanceUntilIdle()
        model.loadData("", 10)
        advanceUntilIdle()
        assertEquals(1, infoRequests)
        assertEquals(listOf(1, 2), pages)
        assertEquals(listOf(1L, 2L), model.replies.value.map { it.rpid })
        model.loadData("BV1", 10, refresh = true)
        advanceUntilIdle()
        assertEquals(2, infoRequests)
        assertEquals(listOf(1, 2, 1), pages)
        assertEquals(listOf(1L), model.replies.value.map { it.rpid })
    }

    @Test
    fun videoReplyErrorsRetrySamePageAndDuplicatePageStopsPagination() = runTest(dispatcher) {
        var fail = true
        val pages = mutableListOf<Int>()
        val model = VideoDetailViewModel(
            loadVideoInfo = { VideoInfo(bvid = it, aid = 10) },
            loadTags = { "" },
            loadRelated = { emptyList() },
            loadReplyPage = { _, page ->
                pages += page
                if (page == 2 && fail) throw IllegalStateException("offline")
                ReplyApi.ReplyPage(listOf(Reply(rpid = 1), Reply(rpid = 1)), true)
            },
            loadReplyCount = { 2L }
        )
        model.loadData("BV1", 10)
        advanceUntilIdle()
        model.loadReplies()
        advanceUntilIdle()
        assertEquals("offline", model.replyErrorMessage.value)
        assertEquals(1, model.replies.value.size)
        assertTrue(model.hasMoreReplies.value)
        fail = false
        model.loadReplies()
        advanceUntilIdle()
        assertNull(model.replyErrorMessage.value)
        assertFalse(model.hasMoreReplies.value)
        model.loadReplies()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
    }

    @Test
    fun switchingVideosCancelsPendingLoadAndNeverUsesOldComments() = runTest(dispatcher) {
        val pending = CompletableDeferred<VideoInfo?>()
        val model = VideoDetailViewModel(
            loadVideoInfo = { if (it == "old") pending.await() else VideoInfo(bvid = it, aid = 20) },
            loadTags = { "" },
            loadRelated = { emptyList() },
            loadReplyPage = { oid, _ -> ReplyApi.ReplyPage(listOf(Reply(rpid = oid)), false) },
            loadReplyCount = { 1L }
        )
        model.loadData("old", 10)
        runCurrent()
        model.loadData("new", 20)
        advanceUntilIdle()
        assertEquals("new", model.videoInfo.value?.bvid)
        assertEquals(listOf(20L), model.replies.value.map { it.rpid })
        assertFalse(model.isLoading.value)
    }

    @Test
    fun childRepliesRetryWithoutSkippingAndStopAfterFinalPage() = runTest(dispatcher) {
        val pages = mutableListOf<Int>()
        var fail = true
        val model = ReplyDetailViewModel { _, _, page, _ ->
            pages += page
            if (page == 2 && fail) throw IllegalStateException("offline")
            ReplyApi.ReplyPage(if (page == 1) listOf(Reply(rpid = 1)) else listOf(Reply(rpid = 1), Reply(rpid = 2)), page == 1)
        }
        model.initData(Reply(rpid = 10, oid = 20))
        model.loadMore()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals("offline", model.errorMessage.value)
        fail = false
        model.loadMore()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(1L, 2L), model.childReplies.value.map { it.rpid })
        assertFalse(model.hasMoreReplies.value)
    }

    @Test
    fun emptyChildPageEndsEvenWhenRootCountIsStale() = runTest(dispatcher) {
        var requests = 0
        val model = ReplyDetailViewModel { _, _, _, _ -> requests++; ReplyApi.ReplyPage(emptyList(), false) }
        val root = Reply(rpid = 10, oid = 20, childCount = 99)
        model.initData(root)
        advanceUntilIdle()
        model.initData(root)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(1, requests)
        assertFalse(model.hasMoreReplies.value)
    }

    @Test
    fun historyRefreshUpdatesPlaybackProgressWithoutReloadingComments() = runTest(dispatcher) {
        var infoRequests = 0
        var historyRequests = 0
        var progress = 20
        val pages = mutableListOf<Int>()
        val model = VideoDetailViewModel(
            loadVideoInfo = { infoRequests++; VideoInfo(bvid = it, aid = 10, cids = listOf(100L)) },
            loadTags = { "" },
            loadRelated = { emptyList() },
            loadReplyPage = { _, page -> pages += page; ReplyApi.ReplyPage(listOf(Reply(rpid = page.toLong())), true) },
            loadReplyCount = { 3L },
            loadHistory = { aid, cid ->
                assertEquals(10L, aid)
                assertEquals(100L, cid)
                historyRequests++
                History(cid, progress)
            },
            monotonicMillis = { testScheduler.currentTime }
        )
        model.loadData("BV1", 10)
        advanceUntilIdle()
        assertEquals(0, historyRequests)
        model.refreshHistory()
        assertEquals(20, model.videoInfo.value?.history?.progress)
        model.loadReplies()
        advanceUntilIdle()
        val savedComments = model.replies.value
        progress = 80
        model.loadData("BV1", 10)
        val refresh = launch { model.refreshHistory() }
        advanceUntilIdle()
        refresh.join()
        assertEquals(80, model.videoInfo.value?.history?.progress)
        assertEquals(savedComments, model.replies.value)
        assertEquals(listOf(1, 2), pages)
        assertEquals(1, infoRequests)
        assertEquals(2, historyRequests)
    }

    @Test
    fun historyRefreshCoalescesInflightCallsThrottlesAndPropagatesCancellation() = runTest(dispatcher) {
        var requests = 0
        val model = VideoDetailViewModel(
            loadVideoInfo = { VideoInfo(bvid = it, aid = 10, cids = listOf(100L)) },
            loadTags = { "" },
            loadRelated = { emptyList() },
            loadReplyPage = { _, _ -> ReplyApi.ReplyPage(emptyList(), false) },
            loadReplyCount = { 0L },
            loadHistory = { _, cid -> requests++; delay(50); History(cid, requests) },
            monotonicMillis = { testScheduler.currentTime }
        )
        model.loadData("BV1", 10)
        advanceUntilIdle()
        launch { model.refreshHistory() }
        launch { model.refreshHistory() }
        advanceUntilIdle()
        assertEquals(1, requests)
        val cancelled = launch { model.refreshHistory() }
        runCurrent()
        advanceTimeBy(100)
        cancelled.cancel()
        runCurrent()
        assertTrue(cancelled.isCancelled)
        assertEquals(1, requests)
        launch { model.refreshHistory() }
        runCurrent()
        advanceTimeBy(849)
        runCurrent()
        assertEquals(1, requests)
        advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(2, model.videoInfo.value?.history?.progress)
    }

    @Test
    fun bangumiRetryClearsErrorAndCanContinueToFinalPage() = runTest(dispatcher) {
        var fail = true
        val pages = mutableListOf<Int>()
        val model = BangumiDetailViewModel(
            loadBangumi = { Bangumi(sectionList = listOf(Bangumi.Section(episodes = listOf(Bangumi.Episode(aid = 10))))) },
            loadReplyPage = { _, page ->
                pages += page
                if (page == 2 && fail) throw IllegalStateException("offline")
                ReplyApi.ReplyPage(listOf(Reply(rpid = page.toLong())), page < 3)
            },
            loadReplyCount = { 3L }
        )
        model.loadData(1)
        assertTrue(model.isLoading.value)
        advanceUntilIdle()
        model.loadReplies()
        model.loadReplies()
        assertTrue(model.isReplyLoading.value)
        advanceUntilIdle()
        assertEquals("offline", model.replyErrorMessage.value)
        fail = false
        model.loadReplies()
        assertNull(model.replyErrorMessage.value)
        advanceUntilIdle()
        assertNull(model.replyErrorMessage.value)
        assertTrue(model.hasMoreReplies.value)
        model.loadReplies()
        advanceUntilIdle()
        model.loadReplies()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 2, 3), pages)
        assertEquals(listOf(1L, 2L, 3L), model.replies.value.map { it.rpid })
        assertFalse(model.hasMoreReplies.value)
        assertFalse(model.isReplyLoading.value)
    }

    @Test
    fun bangumiCancelledPageIsNotReportedAsFailureAndCanRetry() = runTest(dispatcher) {
        var requests = 0
        val model = BangumiDetailViewModel(
            loadBangumi = { Bangumi(sectionList = listOf(Bangumi.Section(episodes = listOf(Bangumi.Episode(aid = 10))))) },
            loadReplyPage = { _, page ->
                requests++
                assertEquals(1, page)
                if (requests == 1) throw CancellationException("cancelled")
                ReplyApi.ReplyPage(listOf(Reply(rpid = 1)), false)
            },
            loadReplyCount = { 1L }
        )
        model.loadData(1)
        advanceUntilIdle()
        assertNull(model.replyErrorMessage.value)
        assertFalse(model.isReplyLoading.value)
        assertTrue(model.hasMoreReplies.value)
        model.loadReplies()
        advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(listOf(1L), model.replies.value.map { it.rpid })
        assertFalse(model.hasMoreReplies.value)
    }

    @Test
    fun pageMetadataDeterminesFinalPageWithoutCountingPinnedComments() {
        assertTrue(ReplyApi.hasMoreReplyPages(1, 20, 20, 21))
        assertFalse(ReplyApi.hasMoreReplyPages(2, 1, 20, 21))
        assertFalse(ReplyApi.hasMoreReplyPages(1, 0, 20, 100))
        assertTrue(ReplyApi.hasMoreReplyPages(1, 1, 0, null))
        assertFalse(ReplyApi.hasMoreReplyPages(Int.MAX_VALUE, 1, 20, Int.MAX_VALUE))
    }
}
