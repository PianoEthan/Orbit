package com.qx.orbit.bili.vision

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.qx.orbit.bili.core.api.CoreTripleResult
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.CoreVideoDetails
import com.qx.orbit.bili.core.api.CoreVideoRelation
import com.qx.orbit.bili.core.api.UserDataGateway
import com.qx.orbit.bili.core.api.VideoGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisionVideoActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unauthenticatedShowsLoginPrompt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        VisionUserSession.logout(context)
        var loginClicked = false
        val video = CoreVideo(42, "BV-test", "标题", "", "UP")
        val gateway = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = CoreVideoDetails(video, "", 60, 1, 2, 3)
        }
        compose.setContent {
            val model = remember { VisionDetailViewModel(gateway) }
            VisionTheme { VisionVideoActions(model, onLogin = { loginClicked = true }) }
        }
        compose.onNodeWithText("登录后点赞、投币或三连").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(loginClicked) }
    }

    @Test fun authenticatedCoinAndTripleShowConfirmationDialogsAndCanCancel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        VisionUserSession.onLoginSuccess(context, "DedeUserID=123; SESSDATA=fake; bili_jct=csrf", 123)
        try {
            var coinsSent = 0
            var tripleSent = 0
            val video = CoreVideo(42, "BV-test", "标题", "", "UP")
            val gateway = object : VideoGateway {
                override suspend fun loadDetails(video: CoreVideo) = CoreVideoDetails(video, "", 60, 1, 2, 3)
                override suspend fun loadRelation(aid: Long, cookie: String) = CoreVideoRelation(liked = false, coinCount = 0, favorited = false)
                override suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String): Boolean {
                    coinsSent += count
                    return true
                }
                override suspend fun tripleVideo(aid: Long, cookie: String, csrf: String): CoreTripleResult {
                    tripleSent++
                    return CoreTripleResult(true, true, true, 2)
                }
            }
            compose.setContent {
                val inputMode = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
                val model = remember {
                    VisionDetailViewModel(gateway).apply {
                        setAccount("DedeUserID=123; SESSDATA=fake; bili_jct=csrf")
                        load(video)
                    }
                }
                VisionTheme { VisionVideoActions(model, onLogin = {}) }
            }

            compose.onNodeWithText("投币").assertIsEnabled().performClick()
            compose.onNodeWithText("投 1 枚硬币？").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("投 1 枚硬币？").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, coinsSent) }

            compose.onNodeWithText("一键三连").assertIsDisplayed().performClick()
            compose.onNodeWithText("确认一键三连？").assertIsDisplayed()
            compose.onNodeWithText("确认").performClick()
            compose.onNodeWithText("确认一键三连？").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, tripleSent) }
        } finally {
            VisionUserSession.logout(context)
        }
    }
}
