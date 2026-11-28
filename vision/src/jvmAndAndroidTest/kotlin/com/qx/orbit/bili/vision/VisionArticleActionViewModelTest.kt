package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.ArticleGateway
import com.qx.orbit.bili.core.api.CoreArticle
import com.qx.orbit.bili.core.api.CoreArticleStats
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
class VisionArticleActionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeGateway(val result: Boolean = true) : ArticleGateway {
        var likeArg: Boolean? = null
        var favArg: Boolean? = null
        var coinMultiply: Int? = null
        override suspend fun loadArticle(cv: Long, cookie: String) = CoreArticle(cv.toString(), "", "", 0, "", "", "", CoreArticleStats(), emptyList())
        override suspend fun loadOpus(id: String, cookie: String) = loadArticle(0, cookie)
        override suspend fun likeArticle(cv: Long, like: Boolean, cookie: String, csrf: String): Boolean { likeArg = like; return result }
        override suspend fun favoriteArticle(cv: Long, add: Boolean, cookie: String, csrf: String): Boolean { favArg = add; return result }
        override suspend fun coinArticle(cv: Long, upid: Long, multiply: Int, cookie: String, csrf: String): Boolean { coinMultiply = multiply; return result }
    }

    @Test fun likeTogglesState() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionArticleViewModel(gateway)
        viewModel.like(2, "session", "csrf")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.liked)
        assertEquals(true, gateway.likeArg)
        assertEquals("点赞成功", viewModel.state.value.message)
        viewModel.like(2, "session", "csrf")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.liked)
        assertEquals(false, gateway.likeArg)
    }

    @Test fun favoriteTogglesState() = runTest(dispatcher) {
        val viewModel = VisionArticleViewModel(FakeGateway())
        viewModel.favorite(2, "session", "csrf")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.favorited)
        assertEquals("收藏成功", viewModel.state.value.message)
    }

    @Test fun coinUsesSingleMultiply() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionArticleViewModel(gateway)
        viewModel.coin(2, 7, "session", "csrf")
        advanceUntilIdle()
        assertEquals(1, gateway.coinMultiply)
        assertEquals("投币成功", viewModel.state.value.message)
    }

    @Test fun failureKeepsStateAndMessages() = runTest(dispatcher) {
        val viewModel = VisionArticleViewModel(FakeGateway(result = false))
        viewModel.like(2, "session", "csrf")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.liked)
        assertEquals("操作失败，请重试", viewModel.state.value.message)
    }
}
