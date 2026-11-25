package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreDynamic
import com.qx.orbit.bili.core.api.CoreDynamicPage
import com.qx.orbit.bili.core.api.DynamicGateway
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
class VisionDynamicDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeGateway(
        val detail: CoreDynamic? = null,
        val detailError: Boolean = false,
        val repostResult: Boolean = true,
    ) : DynamicGateway {
        var repostCalls = 0
        override suspend fun load(cookie: String, offset: String) = CoreDynamicPage(emptyList(), null)
        override suspend fun loadDetail(id: String, cookie: String): CoreDynamic {
            if (detailError) error("boom")
            return detail ?: error("missing")
        }
        override suspend fun repost(dynamicId: String, text: String, cookie: String, csrf: String): Boolean {
            repostCalls++
            return repostResult
        }
    }

    private val dynamic = CoreDynamic("99", "用户", "", "刚刚", "正文", emptyList())

    @Test fun openLoadsDetail() = runTest(dispatcher) {
        val viewModel = VisionDynamicDetailViewModel(FakeGateway(detail = dynamic))
        viewModel.open("99", "session")
        advanceUntilIdle()
        assertEquals(dynamic, viewModel.state.value.dynamic)
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
    }

    @Test fun openFailureSetsError() = runTest(dispatcher) {
        val viewModel = VisionDynamicDetailViewModel(FakeGateway(detailError = true))
        viewModel.open("99", "session")
        advanceUntilIdle()
        assertNull(viewModel.state.value.dynamic)
        assertEquals("动态加载失败，请重试", viewModel.state.value.error)
    }

    @Test fun repostSuccessMarksReposted() = runTest(dispatcher) {
        val gateway = FakeGateway(detail = dynamic, repostResult = true)
        val viewModel = VisionDynamicDetailViewModel(gateway)
        viewModel.repost("99", "说说", "session", "csrf")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reposted)
        assertEquals("转发成功", viewModel.state.value.repostMessage)
        assertEquals(1, gateway.repostCalls)
    }

    @Test fun repostFailureKeepsEditable() = runTest(dispatcher) {
        val viewModel = VisionDynamicDetailViewModel(FakeGateway(repostResult = false))
        viewModel.repost("99", "说说", "session", "csrf")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.reposted)
        assertEquals("转发失败，请重试", viewModel.state.value.repostMessage)
    }

    @Test fun repostIgnoredAfterSuccess() = runTest(dispatcher) {
        val gateway = FakeGateway(repostResult = true)
        val viewModel = VisionDynamicDetailViewModel(gateway)
        viewModel.repost("99", "说说", "session", "csrf")
        advanceUntilIdle()
        viewModel.repost("99", "再说", "session", "csrf")
        advanceUntilIdle()
        assertEquals(1, gateway.repostCalls)
    }
}
