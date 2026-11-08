package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoDetails
import com.qx.orbit.bili.core.api.CoreVideoPage
import com.qx.orbit.bili.core.api.CoreVideoReply
import com.qx.orbit.bili.core.api.CoreVideoRelation
import com.qx.orbit.bili.core.api.VideoGateway
import com.qx.orbit.bili.core.api.UserDataGateway
import com.qx.orbit.bili.core.api.CoreHistoryItem
import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.CoreReplyPage
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun video(id: Long) = CoreVideo(id, "BV$id", "标题$id", "", "UP")
    private fun details(video: CoreVideo, pages: List<CoreVideoPage> = emptyList()) =
        CoreVideoDetails(video, "简介", 60, 1, 2, 3, pages)

    private fun gateway(
        load: suspend (CoreVideo) -> CoreVideoDetails,
        replies: suspend (Long) -> List<CoreVideoReply> = { emptyList() },
        like: suspend (Long, Boolean, String, String) -> Boolean = { _, _, _, _ -> true },
        coin: suspend (Long, Int, String, String) -> Boolean = { _, _, _, _ -> true },
    ) = object : VideoGateway {
        override suspend fun loadDetails(video: CoreVideo) = load(video)
        override suspend fun loadReplies(aid: Long) = replies(aid)
        override suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String) = like(aid, like, cookie, csrf)
        override suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String) = coin(aid, count, cookie, csrf)
    }

    @Test fun commentsUseCurrentAccountAndIgnoreOldAccountResponse() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreReplyPage>()
        val cookies = mutableListOf<String>()
        val fresh = CoreVideoReply(2, "新用户", "", "新账号评论", 0, pinned = true)
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadReplyPage(aid: Long, offset: String, cookie: String): CoreReplyPage {
                cookies += cookie
                return if (cookie == "old") withContext(NonCancellable) { old.await() }
                else CoreReplyPage(listOf(fresh), null, 1)
            }
        }
        val model = VisionDetailViewModel(gateway)
        model.setAccount("old"); model.load(video(42)); runCurrent()
        model.setAccount("new"); runCurrent()
        assertEquals(listOf(fresh), model.replies.value)
        old.complete(CoreReplyPage(listOf(fresh.copy(rpid = 1, message = "旧账号评论")), null, 1)); advanceUntilIdle()
        assertEquals(listOf("old", "new"), cookies)
        assertEquals(listOf(fresh), model.replies.value)
        model.setAccount(""); advanceUntilIdle(); assertEquals("", cookies.last())
    }

    @Test fun historyResumeOnlyAppliesToTheOriginalEpisode() = runTest(dispatcher) {
        val pages = listOf(CoreVideoPage(1, "第一集", 101, 60), CoreVideoPage(2, "第二集", 102, 90))
        val model = VisionDetailViewModel(gateway(load = { details(it, pages) }))
        model.load(video(42).copy(cid = 102, startPositionMs = 45000)); advanceUntilIdle()
        assertEquals(45000L, model.playbackVideo()?.startPositionMs)
        model.selectPage(pages[0]); assertEquals(0L, model.playbackVideo()?.startPositionMs)
        model.selectPage(pages[1]); assertEquals(45000L, model.playbackVideo()?.startPositionMs)
    }

    @Test fun favoriteUsesResolvedAidAndPreventsDuplicateWrites() = runTest(dispatcher) {
        var calls = 0
        val response = CompletableDeferred<Boolean>()
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video.copy(id = 42))
            override suspend fun favVideo(aid: Long, folderId: Long, cookie: String, csrf: String): Boolean {
                calls++
                assertEquals(42L, aid)
                assertEquals(99L, folderId)
                return response.await()
            }
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(0)); advanceUntilIdle()
        model.favorite(99, "cookie", "csrf"); model.favorite(99, "cookie", "csrf"); runCurrent()
        assertEquals(1, calls)
        assertTrue(model.interactionPending.value)
        response.complete(true); advanceUntilIdle()
        assertEquals("已收藏到所选收藏夹", model.interactionMessage.value)
        assertFalse(model.interactionPending.value)
    }

    @Test fun addsWatchLaterUsingResolvedAidAndConfirmsAfterResponse() = runTest(dispatcher) {
        var requestedAid = 0L
        val user = object : UserDataGateway {
            override suspend fun loadHistory(cookie: String) = emptyList<CoreHistoryItem>()
            override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
            override suspend fun loadFavFolders(cookie: String, mid: Long) = emptyList<CoreFavFolder>()
            override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = emptyList<CoreVideo>()
            override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
            override suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String) {
                requestedAid = aid
                assertTrue(add)
            }
        }
        val model = VisionDetailViewModel(gateway(load = { details(it.copy(id = 42)) }), userGateway = user)
        model.load(video(0)); advanceUntilIdle()
        model.addWatchLater("cookie", "csrf"); advanceUntilIdle()
        assertEquals(42L, requestedAid)
        assertEquals("已添加到稍后再看", model.interactionMessage.value)
    }

    @Test fun relationRefreshesAfterUnlikeAndIgnoresOldAccountResponses() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreVideoRelation>()
        var liked = true
        var target: Boolean? = null
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadRelation(aid: Long, cookie: String): CoreVideoRelation {
                if (cookie == "old") return withContext(NonCancellable) { old.await() }
                return CoreVideoRelation(liked, 2, true)
            }
            override suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String): Boolean {
                target = like; liked = like; return true
            }
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(42)); advanceUntilIdle()
        model.setAccount("old"); runCurrent()
        model.setAccount("new"); runCurrent()
        old.complete(CoreVideoRelation(false, 0, false)); advanceUntilIdle()
        assertEquals(CoreVideoRelation(true, 2, true), model.relation.value)
        model.like("new", "csrf"); advanceUntilIdle()
        assertEquals(false, target)
        assertEquals("已取消点赞", model.interactionMessage.value)
        assertEquals(CoreVideoRelation(false, 2, true), model.relation.value)
        model.setAccount(""); advanceUntilIdle()
        assertNull(model.relation.value)
    }

    @Test fun relationFailureBlocksWriteUntilSuccessfulRefresh() = runTest(dispatcher) {
        var fail = true
        var writes = 0
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadRelation(aid: Long, cookie: String): CoreVideoRelation {
                if (fail) error("offline")
                return CoreVideoRelation(false, 0, false)
            }
            override suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String): Boolean { writes++; return true }
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(42)); advanceUntilIdle()
        model.setAccount("account"); advanceUntilIdle()
        assertTrue(model.relationError.value)
        model.coin("account", "csrf"); advanceUntilIdle()
        assertEquals(0, writes)
        fail = false; model.refreshRelation(); advanceUntilIdle()
        model.coin("account", "csrf"); advanceUntilIdle()
        assertEquals(1, writes)
        assertFalse(model.relationError.value)
    }

    @Test fun logoutWhileWritingDoesNotDisplayOldAccountSuccess() = runTest(dispatcher) {
        val result = CompletableDeferred<Boolean>()
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadRelation(aid: Long, cookie: String) = CoreVideoRelation(false, 0, false)
            override suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String) = withContext(NonCancellable) { result.await() }
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(42)); advanceUntilIdle()
        model.setAccount("account"); advanceUntilIdle()
        model.like("account", "csrf"); runCurrent()
        model.setAccount("")
        result.complete(true); advanceUntilIdle()
        assertNull(model.interactionMessage.value)
        assertNull(model.relation.value)
        assertFalse(model.interactionPending.value)
    }

    @Test fun actionsAreIdempotentWhilePendingAndRejectLoggedOutCalls() = runTest(dispatcher) {
        val response = CompletableDeferred<Boolean>()
        var calls = 0
        val model = VisionDetailViewModel(gateway(load = { details(it) }, like = { _, _, _, _ -> calls++; response.await() }))
        model.load(video(42)); advanceUntilIdle()
        model.like("", "")
        assertEquals("请先登录后操作", model.interactionMessage.value)
        assertEquals(0, calls)
        model.like("cookie", "csrf"); model.like("cookie", "csrf"); runCurrent()
        assertTrue(model.interactionPending.value)
        assertEquals(1, calls)
        model.load(video(43)); runCurrent()
        response.complete(true); advanceUntilIdle()
        assertNull(model.interactionMessage.value)
        assertFalse(model.interactionPending.value)
    }

    @Test fun tripleReportsPartialSuccessInsteadOfPretendingAllSucceeded() = runTest(dispatcher) {
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun tripleVideo(aid: Long, cookie: String, csrf: String) = com.qx.orbit.bili.core.api.CoreTripleResult(true, false, true, 0)
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(42)); advanceUntilIdle()
        model.triple("cookie", "csrf"); advanceUntilIdle()
        assertEquals("三连结果：点赞成功、投币未完成、收藏成功", model.interactionMessage.value)
        assertFalse(model.interactionPending.value)
    }

    @Test fun retriesFailedDetailsAndCoalescesSuccessfulLoads() = runTest(dispatcher) {
        var calls = 0
        val model = VisionDetailViewModel(gateway({
            if (++calls == 1) error("离线")
            details(it)
        }))
        model.load(video(1))
        model.load(video(1))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("离线", model.error.value)
        model.retry()
        advanceUntilIdle()
        assertNull(model.error.value)
        assertEquals(details(video(1)), model.details.value)
        model.load(video(1))
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun loadsPagesAndRepliesAndHandlesInteractions() = runTest(dispatcher) {
        val p1 = CoreVideoPage(1, "第一集", 1001, 60)
        val p2 = CoreVideoPage(2, "第二集", 1002, 60)
        val rep = CoreVideoReply(1, "用户1", "avatar", "好视频", 10)
        val model = VisionDetailViewModel(gateway(
            load = { details(it, listOf(p1, p2)) },
            replies = { listOf(rep) },
        ))
        model.load(video(42))
        advanceUntilIdle()
        assertEquals(p1, model.selectedPage.value)
        assertEquals(listOf(rep), model.replies.value)
        model.selectPage(p2)
        assertEquals(p2, model.selectedPage.value)
        assertEquals(1002L, model.playbackVideo()?.cid)
        model.retry()
        advanceUntilIdle()
        assertEquals(p2, model.selectedPage.value)

        model.like("cookie", "csrf")
        advanceUntilIdle()
        assertEquals("点赞成功", model.interactionMessage.value)

        model.coin("cookie", "csrf")
        advanceUntilIdle()
        assertEquals("投币成功", model.interactionMessage.value)
    }

    @Test fun replyPaginationRetriesSameOffsetAndDeduplicates() = runTest(dispatcher) {
        val first = CoreVideoReply(1, "A", "", "第一条", 0)
        val second = first.copy(rpid = 2, message = "第二条")
        var fail = true
        val offsets = mutableListOf<String>()
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadReplyPage(aid: Long, offset: String): CoreReplyPage {
                offsets += offset
                if (offset.isEmpty()) return CoreReplyPage(listOf(first), "next", 2)
                if (fail) error("offline")
                return CoreReplyPage(listOf(first, second), null, 2)
            }
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(1)); advanceUntilIdle()
        model.loadMoreReplies(); model.loadMoreReplies(); advanceUntilIdle()
        assertEquals(listOf(first), model.replies.value)
        assertEquals("评论加载失败，请重试", model.repliesError.value)
        fail = false; model.retryReplies(); advanceUntilIdle()
        assertEquals(listOf("", "next", "next"), offsets)
        assertEquals(listOf(first, second), model.replies.value)
        assertFalse(model.repliesHasMore.value)
        assertNull(model.repliesError.value)
    }

    @Test fun replyPageReturningSameCursorStopsPagination() = runTest(dispatcher) {
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = details(video)
            override suspend fun loadReplyPage(aid: Long, offset: String) = CoreReplyPage(listOf(CoreVideoReply(1, "A", "", "评论", 0)), "next", 1)
        }
        val model = VisionDetailViewModel(gateway)
        model.load(video(1)); advanceUntilIdle()
        model.loadMoreReplies(); advanceUntilIdle()
        assertEquals(1, model.replies.value.size)
        assertFalse(model.repliesHasMore.value)
    }

    @Test fun staleRepliesCannotOverwriteNewVideoAndFailureIsRetryable() = runTest(dispatcher) {
        val old = CompletableDeferred<List<CoreVideoReply>>()
        var fail = true
        val fresh = CoreVideoReply(2, "新用户", "", "新评论", 0)
        val model = VisionDetailViewModel(gateway(
            load = { details(it) },
            replies = { aid ->
                if (aid == 1L) withContext(NonCancellable) { old.await() }
                else if (fail) error("offline") else listOf(fresh)
            },
        ))
        model.load(video(1)); runCurrent()
        model.load(video(2)); runCurrent()
        assertEquals("评论加载失败，请重试", model.repliesError.value)
        old.complete(listOf(CoreVideoReply(1, "旧", "", "旧评论", 0))); runCurrent()
        assertTrue(model.replies.value.isEmpty())
        fail = false
        model.retryReplies(); advanceUntilIdle()
        assertEquals(listOf(fresh), model.replies.value)
        assertNull(model.repliesError.value)
        assertFalse(model.repliesLoading.value)
    }

    @Test fun opensRequestedPartInsteadOfAlwaysSelectingFirst() = runTest(dispatcher) {
        val pages = listOf(CoreVideoPage(1, "第一集", 1001, 60), CoreVideoPage(2, "第二集", 1002, 60))
        val model = VisionDetailViewModel(gateway(load = { details(it, pages) }))
        model.load(video(1).copy(cid = 1002)); advanceUntilIdle()
        assertEquals(1002L, model.selectedPage.value?.cid)
        assertEquals(1002L, model.playbackVideo()?.cid)
    }

    @Test fun switchingVideoClearsPreviousDetailsAndIgnoresLateResponse() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreVideoDetails>()
        val fresh = CompletableDeferred<CoreVideoDetails>()
        val model = VisionDetailViewModel(gateway({
            when (it.id) {
                1L -> details(it)
                2L -> withContext(NonCancellable) { old.await() }
                else -> fresh.await()
            }
        }))
        model.load(video(1))
        advanceUntilIdle()
        model.load(video(2))
        assertNull(model.details.value)
        runCurrent()
        model.load(video(3))
        runCurrent()
        old.complete(details(video(2)))
        runCurrent()
        assertNull(model.details.value)
        assertTrue(model.isLoading.value)
        fresh.complete(details(video(3)))
        advanceUntilIdle()
        assertEquals(details(video(3)), model.details.value)
        assertFalse(model.isLoading.value)
    }
}
