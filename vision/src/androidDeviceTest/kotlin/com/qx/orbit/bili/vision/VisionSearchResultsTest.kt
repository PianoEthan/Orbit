package com.qx.orbit.bili.vision

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.CoreVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisionSearchResultsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstResultStaysBelowHeaderAndPaginationErrorKeepsContentAndRetry() {
        var retries = 0
        val video = CoreVideo(42, "BV-test", "首行搜索结果", "", "作者", durationText = "01:23", viewText = "12.3万")
        compose.setContent {
            VisionTheme {
                SearchRightPane(query = "测试", text = "测试", hotwords = emptyList(), suggestions = emptyList(),
                    results = listOf(video), loading = false, error = "网络中断", hasMore = true, history = emptyList(),
                    onSelectKeyword = {}, onClearSearch = {}, onRefreshHotwords = {}, onClearHistory = {}, onLoadMore = {},
                    onRetry = { retries++ }, onVideoClick = {}, columns = 1)
            }
        }
        val header = compose.onNodeWithText("“测试” 的搜索结果").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("首行搜索结果").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= header.bottom)
        compose.onNodeWithText("12.3万").assertIsDisplayed()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("重试加载更多"))
        compose.onNodeWithText("网络中断").assertIsDisplayed()
        compose.onNodeWithText("重试加载更多").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        list.performScrollToIndex(0)
        compose.onNodeWithText("首行搜索结果").assertIsDisplayed()
    }
}
