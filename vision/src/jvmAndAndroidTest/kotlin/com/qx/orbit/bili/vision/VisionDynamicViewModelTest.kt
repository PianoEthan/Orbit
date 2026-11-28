package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreDynamic
import com.qx.orbit.bili.core.api.CoreDynamicPage
import com.qx.orbit.bili.core.api.DynamicGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionDynamicViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun item(id: String) = CoreDynamic(id, "用户", "", "刚刚", "正文$id", emptyList())

    @Test fun appendRetriesSameOffsetAndKeepsOldContent() = runTest(dispatcher) {
        var fail = true
        val offsets = mutableListOf<String>()
        val model = VisionDynamicViewModel(DynamicGateway { _, offset ->
            offsets += offset
            if (offset.isEmpty()) CoreDynamicPage(listOf(item("1")), "next")
            else if (fail) error("offline") else CoreDynamicPage(listOf(item("1"), item("2")), null)
        })
        model.setAccount("session"); advanceUntilIdle()
        model.loadMore(); model.loadMore(); advanceUntilIdle()
        assertEquals(listOf(item("1")), model.state.value.items)
        assertNotNull(model.state.value.error)
        fail = false; model.retry(); advanceUntilIdle()
        assertEquals(listOf("", "next", "next"), offsets)
        assertEquals(listOf(item("1"), item("2")), model.state.value.items)
        assertFalse(model.state.value.hasMore)
    }

    @Test fun returningAfterInterruptedFirstLoadResumesInsteadOfStayingEmpty() = runTest(dispatcher) {
        var calls = 0
        val old = CompletableDeferred<CoreDynamicPage>()
        val model = VisionDynamicViewModel(DynamicGateway { _, _ ->
            if (++calls == 1) old.await() else CoreDynamicPage(listOf(item("1")), null)
        })
        model.setAccount("session"); runCurrent()
        model.stop()
        model.setAccount("session"); advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(listOf(item("1")), model.state.value.items)
        assertFalse(model.state.value.loading)
    }

    @Test fun logoutAndClosingDiscardLateResponses() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreDynamicPage>()
        val model = VisionDynamicViewModel(DynamicGateway { _, _ -> withContext(NonCancellable) { old.await() } })
        model.setAccount("old"); runCurrent()
        model.setAccount("")
        old.complete(CoreDynamicPage(listOf(item("1")), "next")); advanceUntilIdle()
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.loading)
        model.stop()
    }
}
