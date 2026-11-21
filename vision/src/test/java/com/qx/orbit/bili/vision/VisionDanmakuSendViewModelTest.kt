package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.DanmakuGateway
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionDanmakuSendViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeGateway(val result: Boolean = true, val error: Boolean = false) : DanmakuGateway {
        var calls = 0
        override suspend fun loadXmlDanmaku(cid: Long): ByteArray? = null
        override suspend fun sendDanmaku(cid: Long, bvid: String, msg: String, progressMs: Long, color: Int, mode: Int, cookie: String, csrf: String): Boolean {
            calls++
            if (error) error("boom")
            return result
        }
    }

    @Test fun sendSuccess() = runTest(dispatcher) {
        val viewModel = VisionDanmakuSendViewModel(FakeGateway(result = true))
        viewModel.send(99, "BV", "内容", 1000, 16777215, 1, "session", "csrf")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.sent)
        assertEquals("弹幕已发送", viewModel.state.value.message)
    }

    @Test fun sendFailureKeepsRetryable() = runTest(dispatcher) {
        val viewModel = VisionDanmakuSendViewModel(FakeGateway(result = false))
        viewModel.send(99, "BV", "内容", 1000, 16777215, 1, "session", "csrf")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.sent)
        assertEquals("发送失败，请重试", viewModel.state.value.message)
    }

    @Test fun sendErrorSurfacesMessage() = runTest(dispatcher) {
        val viewModel = VisionDanmakuSendViewModel(FakeGateway(error = true))
        viewModel.send(99, "BV", "内容", 1000, 16777215, 1, "session", "csrf")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.sent)
        assertEquals("boom", viewModel.state.value.message)
    }

    @Test fun ignoresDuplicateAfterSuccess() = runTest(dispatcher) {
        val gateway = FakeGateway(result = true)
        val viewModel = VisionDanmakuSendViewModel(gateway)
        viewModel.send(99, "BV", "内容", 1000, 16777215, 1, "session", "csrf")
        advanceUntilIdle()
        viewModel.send(99, "BV", "再发", 1000, 16777215, 1, "session", "csrf")
        advanceUntilIdle()
        assertEquals(1, gateway.calls)
    }
}
