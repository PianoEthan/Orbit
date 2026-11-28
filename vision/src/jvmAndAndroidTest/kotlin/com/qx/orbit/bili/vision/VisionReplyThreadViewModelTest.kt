package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionReplyThreadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun reply(id: Long) = CoreVideoReply(id, "用户$id", "", "回复$id", 0)
    private fun gateway(load: suspend (Long, Int) -> CoreReplyThreadPage) = object : VideoGateway {
        override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails = error("Unused")
        override suspend fun loadReplyThread(aid: Long, root: Long, page: Int, cookie: String) = load(root, page)
    }

    @Test fun failedAppendRetriesPageAndKeepsRootAndExistingReplies() = runTest(dispatcher) {
        var fail = true
        val pages = mutableListOf<Int>()
        val model = VisionReplyThreadViewModel(gateway { root, page ->
            pages += page
            if (page == 2 && fail) error("offline")
            CoreReplyThreadPage(reply(root), if (page == 1) listOf(reply(1)) else listOf(reply(1), reply(2)), page == 1)
        })
        model.open(42, 99, ""); advanceUntilIdle()
        model.loadMore(); model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(reply(1)), model.state.value.replies)
        assertNotNull(model.state.value.error)
        fail = false; model.retry(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(reply(1), reply(2)), model.state.value.replies)
        assertEquals(reply(99), model.state.value.root)
        assertFalse(model.state.value.hasMore)
    }

    @Test fun closingBeforeReplyArrivesKeepsPageEmpty() = runTest(dispatcher) {
        val response = CompletableDeferred<CoreReplyThreadPage>()
        val model = VisionReplyThreadViewModel(gateway { _, _ -> withContext(NonCancellable) { response.await() } })
        model.open(42, 99, ""); runCurrent()
        model.stop()
        response.complete(CoreReplyThreadPage(reply(99), listOf(reply(1)), true)); advanceUntilIdle()
        assertNull(model.state.value.root)
        assertTrue(model.state.value.replies.isEmpty())
        assertFalse(model.state.value.loading)
    }

    @Test fun switchingThreadsAndClosingDiscardLateRequests() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreReplyThreadPage>()
        val model = VisionReplyThreadViewModel(gateway { root, _ ->
            if (root == 99L) withContext(NonCancellable) { old.await() }
            else CoreReplyThreadPage(reply(root), listOf(reply(2)), false)
        })
        model.open(42, 99, ""); runCurrent()
        model.open(42, 100, ""); runCurrent()
        old.complete(CoreReplyThreadPage(reply(99), listOf(reply(1)), false)); advanceUntilIdle()
        assertEquals(reply(100), model.state.value.root)
        assertEquals(listOf(reply(2)), model.state.value.replies)
        model.stop()
        assertFalse(model.state.value.loading)
    }
}
