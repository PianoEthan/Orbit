package com.qx.orbit.bili.vision

import androidx.lifecycle.SavedStateHandle
import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionReplyComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun gateway(send: suspend (String) -> Long) = object : VideoGateway {
        override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails = error("Unused")
        override suspend fun sendReply(aid: Long, root: Long, parent: Long, message: String, cookie: String, csrf: String) = send(message)
    }
    private fun model(saved: SavedStateHandle = SavedStateHandle(), send: suspend (String) -> Long) =
        VisionReplyComposerViewModel(saved, gateway(send)) { "session" }.also { it.setAccount("session") }

    @Test fun requiresConfirmationAndSuppressesDoubleSend() = runTest(dispatcher) {
        var calls = 0
        val response = CompletableDeferred<Long>()
        val vm = model { calls++; assertEquals("测试内容", it); response.await() }
        vm.edit("测试内容")
        vm.send(42, 0, 0, "csrf"); runCurrent(); assertEquals(0, calls)
        vm.requestConfirmation(); vm.dismissConfirmation()
        vm.send(42, 0, 0, "csrf"); runCurrent(); assertEquals(0, calls)
        vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); vm.send(42, 0, 0, "csrf"); runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.sending)
        vm.edit("不应覆盖"); assertEquals("测试内容", vm.draft.value)
        response.complete(123); advanceUntilIdle()
        assertEquals(123L, vm.state.value.sentId); assertEquals("", vm.draft.value)
        vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); advanceUntilIdle(); assertEquals(1, calls)
    }

    @Test fun unknownResultRetainsDraftAndNeverRetriesWithoutAcknowledgment() = runTest(dispatcher) {
        var calls = 0
        val saved = SavedStateHandle()
        val vm = model(saved) { calls++; error("timeout") }
        vm.edit("保留草稿"); vm.requestConfirmation(); vm.send(42, 99, 101, "csrf"); advanceUntilIdle()
        assertTrue(vm.state.value.uncertain); assertEquals("保留草稿", vm.draft.value)
        vm.requestConfirmation(); vm.send(42, 99, 101, "csrf"); advanceUntilIdle(); assertEquals(1, calls)
        val restored = model(saved) { calls++; 123 }
        assertTrue(restored.state.value.uncertain)
        assertEquals("保留草稿", restored.draft.value)
        restored.acknowledgeUncertain(); restored.requestConfirmation(); restored.send(42, 99, 101, "csrf"); advanceUntilIdle()
        assertEquals(2, calls); assertEquals(123L, restored.state.value.sentId)
    }

    @Test fun explicitRejectionAllowsManualRetryAndPreservesText() = runTest(dispatcher) {
        val vm = model { throw CoreReplyRejectedException("评论区已关闭") }
        vm.edit("草稿"); vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); advanceUntilIdle()
        assertFalse(vm.state.value.uncertain); assertEquals("评论区已关闭", vm.state.value.message)
        assertEquals("草稿", vm.draft.value)
        vm.requestConfirmation(); assertTrue(vm.state.value.confirming)
    }

    @Test fun accountChangeAndClosingIgnoreLateSuccess() = runTest(dispatcher) {
        val response = CompletableDeferred<Long>()
        val vm = model { withContext(NonCancellable) { response.await() } }
        vm.edit("草稿"); vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); runCurrent()
        vm.setAccount("new-session"); response.complete(123); advanceUntilIdle()
        assertNull(vm.state.value.sentId); assertTrue(vm.state.value.uncertain)
        assertEquals("草稿", vm.draft.value)
    }

    @Test fun returningToSameEntryKeepsDraftButClosingSuppressesLateSuccess() = runTest(dispatcher) {
        val response = CompletableDeferred<Long>()
        val saved = SavedStateHandle()
        val vm = model(saved) { withContext(NonCancellable) { response.await() } }
        vm.edit("尚未发送"); vm.stop(); assertEquals("尚未发送", vm.draft.value)
        vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); runCurrent(); vm.stop()
        response.complete(123); advanceUntilIdle()
        assertNull(vm.state.value.sentId); assertTrue(vm.state.value.uncertain)
        assertTrue(model(saved) { error("must not send") }.state.value.uncertain)
    }

    @Test fun newCommentCanBeWrittenAfterAcknowledgingSuccessfulResult() = runTest(dispatcher) {
        val vm = model { 123 }
        vm.edit("第一条"); vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); advanceUntilIdle()
        vm.clearSentResult(); vm.edit("第二条"); vm.requestConfirmation()
        assertNull(vm.state.value.sentId); assertTrue(vm.state.value.confirming); assertEquals("第二条", vm.draft.value)
    }

    @Test fun rejectsEmptyTooLongAndMissingSessionAndRestoresSuccessfulState() = runTest(dispatcher) {
        var calls = 0
        val saved = SavedStateHandle()
        val vm = model(saved) { calls++; 123 }
        vm.requestConfirmation(); assertFalse(vm.state.value.confirming)
        vm.edit("字".repeat(1001)); vm.requestConfirmation(); assertFalse(vm.state.value.confirming)
        vm.edit("草稿"); vm.setAccount(""); vm.requestConfirmation(); assertFalse(vm.state.value.confirming)
        vm.setAccount("session"); vm.requestConfirmation(); vm.send(42, 0, 0, ""); advanceUntilIdle(); assertEquals(0, calls)
        vm.requestConfirmation(); vm.send(42, 0, 0, "csrf"); advanceUntilIdle()
        assertEquals(123L, model(saved) { error("must not send") }.state.value.sentId)
    }
}
