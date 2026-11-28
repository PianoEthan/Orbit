package com.qx.orbit.bili.vision

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.qx.orbit.bili.core.api.CoreDynamic
import com.qx.orbit.bili.core.api.CoreDynamicPage
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.DynamicGateway
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisionDynamicScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unauthenticatedStateShowsLoginPrompt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeVisionAndroid(context)
        VisionUserSession.logout()
        var loginClicked = false
        compose.setContent {
            VisionTheme { VisionDynamicScreen(onBack = {}, onLogin = { loginClicked = true }, onVideo = {}) }
        }
        compose.onNodeWithText("登录查看关注动态").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, loginClicked) }
    }

    @Test fun dynamicFeedInteractionsNavigateToImagesAndVideos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeVisionAndroid(context)
        VisionUserSession.onLoginSuccess("DedeUserID=123; SESSDATA=fake; bili_jct=csrf", 123)
        try {
            var clickedVideo: CoreVideo? = null
            var openedImageIndex = -1
            val testVideo = CoreVideo(42, "BV-dynamic", "动态视频卡片", "", "动态UP")
            val dynamic = CoreDynamic(
                id = "d1", author = "测试UP", avatar = "", published = "刚刚",
                text = "这是动态测试正文\n第二行\n第三行\n第四行\n第五行\n第六行\n第七行超长折叠",
                images = listOf("https://image.test/1.png", "https://image.test/2.png"),
                video = testVideo,
                original = CoreDynamic(id = "d2", author = "原作者", avatar = "", published = "昨天", text = "原动态内容", images = emptyList()),
            )
            val gateway = DynamicGateway { _, _ -> CoreDynamicPage(listOf(dynamic), null) }

            compose.setContent {
                val model = remember { VisionDynamicViewModel(gateway) }
                VisionTheme {
                    VisionDynamicScreen(
                        onBack = {}, onLogin = {},
                        onVideo = { clickedVideo = it },
                        viewModel = model,
                        onImages = { _, index -> openedImageIndex = index },
                    )
                }
            }

            compose.onNodeWithText("测试UP").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("查看第 1 张动态图片").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(0, openedImageIndex) }
            compose.onNodeWithText("动态视频卡片").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(testVideo, clickedVideo) }
            compose.onNodeWithText("原作者").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("原动态内容").performScrollTo().assertIsDisplayed()
        } finally {
            VisionUserSession.logout()
        }
    }
}
