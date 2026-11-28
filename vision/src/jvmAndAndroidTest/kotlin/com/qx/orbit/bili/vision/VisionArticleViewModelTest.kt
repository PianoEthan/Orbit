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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionArticleViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeGateway(val error: Boolean = false) : ArticleGateway {
        var articleCalls = 0
        var opusCalls = 0
        private fun article(id: String) = CoreArticle(id, "标题", "作者", 1, "", "", "", CoreArticleStats(), emptyList())
        override suspend fun loadArticle(cv: Long, cookie: String): CoreArticle {
            articleCalls++
            if (error) error("boom")
            return article(cv.toString())
        }
        override suspend fun loadOpus(id: String, cookie: String): CoreArticle {
            opusCalls++
            if (error) error("boom")
            return article(id)
        }
    }

    @Test fun openArticleLoadsCv() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionArticleViewModel(gateway)
        viewModel.open("2", false, "")
        advanceUntilIdle()
        assertEquals("2", viewModel.state.value.article?.id)
        assertEquals(1, gateway.articleCalls)
        assertEquals(0, gateway.opusCalls)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun openOpusLoadsOpus() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionArticleViewModel(gateway)
        viewModel.open("99", true, "")
        advanceUntilIdle()
        assertEquals("99", viewModel.state.value.article?.id)
        assertEquals(1, gateway.opusCalls)
        assertEquals(0, gateway.articleCalls)
    }

    @Test fun openFailureSetsError() = runTest(dispatcher) {
        val viewModel = VisionArticleViewModel(FakeGateway(error = true))
        viewModel.open("2", false, "")
        advanceUntilIdle()
        assertNull(viewModel.state.value.article)
        assertEquals("内容加载失败，请重试", viewModel.state.value.error)
    }
}
