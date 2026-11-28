package com.qx.orbit.bili.vision

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.qx.orbit.bili.core.api.CoreReplyThreadPage
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoDetails
import com.qx.orbit.bili.core.api.CoreVideoReply
import com.qx.orbit.bili.core.api.VideoGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisionReplyFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replyThreadDisplaysRootAndChildrenAndTriggersCompose() {
        var composedRpid = -1L
        val rootReply = CoreVideoReply(99, "根用户", "", "楼主评论", 10, replyCount = 1)
        val child = CoreVideoReply(101, "回复者", "", "第一条楼中楼", 2)
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails = error("unused")
            override suspend fun loadReplyThread(aid: Long, root: Long, page: Int, cookie: String): CoreReplyThreadPage =
                CoreReplyThreadPage(root = rootReply, replies = listOf(child), hasMore = false)
        }
        compose.setContent {
            val model = remember { VisionReplyThreadViewModel(gateway) }
            VisionTheme { VisionReplyThreadScreen(42, 99, onBack = {}, viewModel = model, onComposeReply = { composedRpid = it }) }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("原评论"))
        compose.onNodeWithText("原评论").assertIsDisplayed()
        compose.onNodeWithText("楼主评论").assertIsDisplayed()
        list.performScrollToNode(hasText("第一条楼中楼"))
        compose.onNodeWithText("第一条楼中楼").assertIsDisplayed()
        compose.onAllNodesWithText("回复")[0].performClick()
        compose.runOnIdle { assertEquals(99L, composedRpid) }
    }

    @Test fun composerShowsPreviewDialogKeepsDraftAndHandlesRemoteFocus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeVisionAndroid(context)
        VisionUserSession.onLoginSuccess("DedeUserID=123; SESSDATA=fake; bili_jct=csrf", 123)
        try {
            val saved = SavedStateHandle()
            val composer = VisionReplyComposerViewModel(saved) { "session" }
            compose.setContent {
                val inputMode = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                VisionTheme {
                    VisionReplyComposerScreen(
                        aid = 42, root = 99, parent = 101,
                        onBack = {}, onLogin = {}, onSent = {},
                        viewModel = composer,
                    )
                }
            }
            compose.onNode(hasSetTextAction()).performTextInput("友善讨论内容")
            compose.waitForIdle()
            compose.onNodeWithText("友善讨论内容").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("预览并发布").performScrollTo().performClick()
            compose.onNodeWithText("公开发布这条评论？").assertIsDisplayed()
            compose.onNodeWithText("继续编辑").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("公开发布这条评论？").assertDoesNotExist()
            compose.onNodeWithText("友善讨论内容").performScrollTo().assertIsDisplayed()

            compose.onNode(hasSetTextAction()).performScrollTo().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            compose.onNode(hasText("预览并发布") and isFocusable()).assertIsFocused()
        } finally {
            VisionUserSession.logout()
        }
    }
}
