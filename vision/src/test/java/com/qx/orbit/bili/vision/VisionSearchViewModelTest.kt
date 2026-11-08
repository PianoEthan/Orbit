package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreHotword
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.SearchGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun testGateway(
        search: suspend (String, Int) -> List<CoreVideo> = { _, _ -> emptyList() },
        hotwords: suspend () -> List<CoreHotword> = { emptyList() },
        suggestions: suspend (String) -> List<String> = { emptyList() },
    ) = object : SearchGateway {
        override suspend fun searchVideos(keyword: String, page: Int) = search(keyword, page)
        override suspend fun loadHotwords() = hotwords()
        override suspend fun loadSuggestions(keyword: String) = suggestions(keyword)
    }

    private fun video(id: Long) = CoreVideo(id, "BV$id", "视频$id", "https://cover.test/$id", "UP")

    @Test fun leavingResultsDiscardsLateResponseEvenWhenRequestIgnoresCancellation() = runTest(dispatcher) {
        val old = CompletableDeferred<List<CoreVideo>>()
        val model = VisionSearchViewModel(testGateway(search = { _, _ -> withContext(NonCancellable) { old.await() } }), false)
        model.submit("旧搜索"); runCurrent(); model.clearSearch()
        old.complete(listOf(video(1))); advanceUntilIdle()
        assertNull(model.query.value); assertTrue(model.results.value.isEmpty()); assertFalse(model.isLoading.value)
    }

    @Test fun suggestionsFromPreviousTextCannotReplaceNewTextOrReturnAfterSubmit() = runTest(dispatcher) {
        val old = CompletableDeferred<List<String>>()
        val model = VisionSearchViewModel(testGateway(suggestions = { keyword ->
            if (keyword == "old") withContext(NonCancellable) { old.await() } else listOf("new建议")
        }), false)
        model.onTextChanged("old"); advanceTimeBy(250); runCurrent()
        model.onTextChanged("new"); advanceTimeBy(250); runCurrent()
        assertEquals(listOf("new建议"), model.suggestions.value)
        old.complete(listOf("old建议")); advanceUntilIdle()
        assertEquals(listOf("new建议"), model.suggestions.value)
        val late = CompletableDeferred<List<String>>()
        val submitting = VisionSearchViewModel(testGateway(suggestions = { withContext(NonCancellable) { late.await() } }), false)
        submitting.onTextChanged("word"); advanceTimeBy(250); runCurrent()
        submitting.submit("word"); late.complete(listOf("过期建议")); advanceUntilIdle()
        assertTrue(submitting.suggestions.value.isEmpty())
    }

    @Test fun repeatedRetryDuringPendingPageDoesNotCancelAndDuplicateRequest() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<CoreVideo>>()
        var calls = 0
        val model = VisionSearchViewModel(testGateway(search = { _, _ -> calls++; pending.await() }), false)
        model.submit("word"); runCurrent()
        model.retry(); model.retry(); runCurrent()
        pending.complete(listOf(video(1))); advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test fun hotwordRefreshDiscardsLateOldDataAndKeepsExistingWordsOnFailure() = runTest(dispatcher) {
        val old = CompletableDeferred<List<CoreHotword>>()
        var calls = 0
        val model = VisionSearchViewModel(testGateway(hotwords = {
            when (++calls) {
                1 -> withContext(NonCancellable) { old.await() }
                2 -> listOf(CoreHotword("最新热词"))
                else -> error("offline")
            }
        }), false)
        model.loadHotwords(); runCurrent(); model.loadHotwords(); runCurrent()
        old.complete(listOf(CoreHotword("旧热词"))); advanceUntilIdle()
        assertEquals(listOf(CoreHotword("最新热词")), model.hotwords.value)
        model.loadHotwords(); advanceUntilIdle()
        assertEquals(listOf(CoreHotword("最新热词")), model.hotwords.value)
    }

    @Test fun failedPaginationRetriesSamePageWithoutDroppingResults() = runTest(dispatcher) {
        val pages = mutableListOf<Int>()
        var fail = true
        val model = VisionSearchViewModel(testGateway(search = { _, page ->
            pages += page
            if (page == 2 && fail) error("offline")
            listOf(video(page.toLong()))
        }), false)
        model.submit("word"); advanceUntilIdle(); model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(video(1)), model.results.value)
        fail = false; model.retry(); advanceUntilIdle()
        assertEquals(listOf(1, 2, 2), pages)
        assertEquals(listOf(video(1), video(2)), model.results.value)
    }

    @Test fun loadsHotwordsOnInit() = runTest(dispatcher) {
        val model = VisionSearchViewModel(
            testGateway(hotwords = { listOf(CoreHotword("热词1", "热词一"), CoreHotword("热词2")) }),
        )
        advanceUntilIdle()
        assertEquals(2, model.hotwords.value.size)
        assertEquals("热词一", model.hotwords.value[0].showName)
    }

    @Test fun textChangesFetchSuggestionsWithDebounce() = runTest(dispatcher) {
        var requested = ""
        val model = VisionSearchViewModel(
            testGateway(suggestions = { keyword -> requested = keyword; listOf("建议1", "建议2") }),
            loadHotwordsOnStart = false,
        )
        model.onTextChanged("bili")
        advanceTimeBy(100)
        assertEquals("", requested)
        advanceTimeBy(200)
        assertEquals("bili", requested)
        assertEquals(listOf("建议1", "建议2"), model.suggestions.value)
    }

    @Test fun submittingSearchLoadsResultsAndClearsSuggestions() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String, Int>>()
        val model = VisionSearchViewModel(
            testGateway(search = { query, page ->
                requests += query to page
                listOf(video(page.toLong()))
            }),
            loadHotwordsOnStart = false,
        )
        model.submit("bilibili")
        advanceUntilIdle()
        assertEquals("bilibili", model.query.value)
        assertEquals(listOf(video(1)), model.results.value)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("bilibili" to 1, "bilibili" to 2), requests)
        assertEquals(listOf(video(1), video(2)), model.results.value)

        model.clearSearch()
        assertNull(model.query.value)
        assertEquals("", model.text.value)
        assertEquals(emptyList<CoreVideo>(), model.results.value)
    }
}
