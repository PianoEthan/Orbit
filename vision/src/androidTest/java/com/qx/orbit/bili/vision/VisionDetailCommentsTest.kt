package com.qx.orbit.bili.vision

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.CoreReplyPage
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoDetails
import com.qx.orbit.bili.core.api.CoreVideoReply
import com.qx.orbit.bili.core.api.VideoGateway
import org.junit.Rule
import org.junit.Test

class VisionDetailCommentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinnedCommentHasVisibleLabelAndReplyEntry() {
        val video = CoreVideo(42, "BV-test", "评论展示测试", "", "作者", cid = 101)
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = CoreVideoDetails(video, "简介", 60, 1, 2, 3)
            override suspend fun loadReplyPage(aid: Long, offset: String, cookie: String) = CoreReplyPage(
                listOf(CoreVideoReply(99, "置顶作者", "", "这是一条置顶评论", 5, replyCount = 2, pinned = true)), null, 1)
        }
        compose.setContent {
            val model = remember { VisionDetailViewModel(gateway) }
            VisionTheme { VisionDetailScreen(video, {}, {}, viewModel = model) }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("评论"))
        compose.onNodeWithText("评论").performClick()
        list.performScrollToNode(hasText("置顶评论"))
        compose.onNodeWithText("置顶评论").assertIsDisplayed()
        compose.onNodeWithText("这是一条置顶评论").assertIsDisplayed()
        list.performScrollToNode(hasText("查看 2 条回复"))
        compose.onNodeWithText("查看 2 条回复").assertIsDisplayed()
    }
}
