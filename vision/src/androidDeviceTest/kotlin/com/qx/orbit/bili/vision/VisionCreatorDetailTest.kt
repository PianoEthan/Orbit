package com.qx.orbit.bili.vision

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisionCreatorDetailTest {
    @get:Rule val compose = createComposeRule()
    private val video = CoreVideo(1, "BV1", "当前视频", "", "UP 主", cid = 101)
    private val next = CoreVideo(2, "BV2", "合集下一条", "", "UP 主", cid = 102)

    @Test fun detailAuthorCollectionAndRelatedNavigateIndependently() {
        var openedMid = 0L
        var openedVideo = ""
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = CoreVideoDetails(video, "简介", 10, 1, 1, 1,
                ownerMid = 123, collectionTitle = "测试合集", collectionVideos = listOf(next))
        }
        compose.setContent {
            val model = remember { VisionDetailViewModel(gateway) }
            val related = remember { VisionRelatedViewModel(RelatedGateway { _, _ -> listOf(next.copy(title = "相关推荐视频")) }) }
            VisionTheme { VisionDetailScreen(video, {}, {}, model, onCreator = { openedMid = it },
                onVideo = { openedVideo = it.bvid }, relatedModel = related) }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("UP 主 · 查看 UP 主空间"))
        compose.onNodeWithText("UP 主 · 查看 UP 主空间").performClick()
        compose.runOnIdle { assertEquals(123L, openedMid) }
        list.performScrollToNode(hasText("合集下一条"))
        compose.onNodeWithText("合集下一条").performClick()
        compose.runOnIdle { assertEquals("BV2", openedVideo) }
        list.performScrollToNode(hasText("相关推荐视频"))
        compose.onNodeWithText("相关推荐视频").assertIsDisplayed().performClick()
    }

    @Test fun creatorShowsProfileAndPostsWithLoginEntry() {
        var login = false
        val gateway = object : CreatorGateway {
            override suspend fun loadCreator(mid: Long, cookie: String) = CoreCreator(mid, "作者测试", "", "签名", 12, 2, false)
            override suspend fun loadVideos(mid: Long, page: Int, cookie: String) = CoreCreatorVideos(listOf(video), false)
            override suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String) = error("Must not submit real relation")
        }
        compose.setContent {
            val model = remember { VisionCreatorViewModel(gateway) }
            VisionTheme { VisionCreatorScreen(123, {}, { login = true }, {}, viewModel = model) }
        }
        compose.onNodeWithText("作者测试").assertIsDisplayed()
        compose.onNodeWithText("关注 UP 主").performClick()
        compose.runOnIdle { assertEquals(true, login) }
        compose.onNodeWithText("当前视频").assertIsDisplayed()
    }
}
