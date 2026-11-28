package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.CoreUserProfile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisionUserComponentsTest {
    @get:Rule val compose = createComposeRule()
    private val longName = "这是一个用于检验手机大字体与大屏换行的很长很长的账号名称"

    @Test fun allTabsCanBeRevealedAndSelectedAtDoubleFontScale() {
        var selected = UserCenterTab.HISTORY
        compose.setContent {
            var tab by remember { mutableStateOf(UserCenterTab.HISTORY) }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                VisionTheme { VisionUserTabs(tab) { tab = it; selected = it } }
            }
        }
        UserCenterTab.entries.forEach { tab ->
            compose.onNodeWithText(tab.title).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(tab.title).assertIsSelected()
            compose.runOnIdle { assertEquals(tab, selected) }
        }
    }

    @Test fun longProfileNameAndActionsRemainReadableAtDoubleFontScale() {
        var logouts = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                VisionTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        VisionUserProfileHeader(CoreUserProfile(1234567890123, longName, "", 12345.5, 6, true), 1234567890123) { logouts++ }
                    }
                }
            }
        }
        compose.onNodeWithText(longName).performScrollTo().assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longName).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse(results.single().hasVisualOverflow)
        compose.onNodeWithText("UID: 1234567890123").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("退出登录").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
    }

    @Test fun remoteOpensAtSelectedFolderAndCanChooseNextOffscreenFolder() {
        var selection: Long? = null
        val folders = (1..30).map { CoreFavFolder(it.toLong(), "收藏夹 $it", it, "") }
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            VisionTheme { VisionUserFolderPicker(folders, 25, onSelect = { selection = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("当前 · 收藏夹 25").assertIsDisplayed()
        compose.onNode(hasText("当前 · 收藏夹 25") and isFocusable()).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(hasText("收藏夹 26") and isFocusable()).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(26L, selection) }
    }

    @Test fun longFolderNameIsNotTruncatedAtDoubleFontScale() {
        val title = "用于测试长标题换行的收藏夹名称，不应挤掉关闭按钮或裁剪文字"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                VisionTheme { VisionUserFolderPicker(listOf(CoreFavFolder(1, title, 123, "")), 1, {}, {}) }
            }
        }
        compose.onNodeWithText("当前 · $title").assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("当前 · $title", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertFalse("width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, size=${layout.size}, lines=${layout.lineCount}", layout.hasVisualOverflow)
        compose.onNodeWithText("关闭").assertIsDisplayed()
    }
}
