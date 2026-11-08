package com.qx.orbit.bili.vision

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsMainEntriesNavigateToSections() {
        var opened: VisionSettingsSection? = null
        compose.setContent { VisionTheme { VisionSettingsScreen(onBack = {}, onOpenSection = { opened = it }) } }
        compose.onNodeWithText("外观与主题").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(VisionSettingsSection.APPEARANCE, opened) }
        compose.onNodeWithText("播放与控制").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(VisionSettingsSection.PLAYBACK, opened) }
        compose.onNodeWithText("输入与遥控器").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(VisionSettingsSection.INPUT, opened) }
        compose.onNodeWithText("关于 Orbit Vision").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(VisionSettingsSection.ABOUT, opened) }
    }

    @Test fun appearanceSectionTogglesAndRemoteNavigation() {
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            VisionTheme { VisionSettingsSectionScreen(VisionSettingsSection.APPEARANCE, onBack = {}) }
        }
        compose.onNodeWithText("跟随系统").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("跟随系统") and isFocusable()).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNode(hasText("跟随系统") and isFocusable()).assertIsFocused()
        compose.onNode(hasText("跟随系统") and isFocusable()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(hasText("浅色模式") and isFocusable()).assertIsFocused()
        compose.onNode(hasText("浅色模式") and isFocusable()).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("浅色模式").assertIsSelected()
        compose.runOnIdle { assertEquals(VisionThemeMode.LIGHT, VisionPreferences.themeMode) }
    }

    @Test fun playbackTimeoutSelectionPersistsPreference() {
        compose.setContent { VisionTheme { VisionSettingsSectionScreen(VisionSettingsSection.PLAYBACK, onBack = {}) } }
        compose.onNodeWithText("8 秒").performScrollTo().performClick()
        compose.onNodeWithText("8 秒").assertIsSelected()
        compose.runOnIdle { assertEquals(8000L, VisionPreferences.controlsTimeoutMs) }
        compose.onNodeWithText("不自动隐藏").performScrollTo().performClick()
        compose.onNodeWithText("不自动隐藏").assertIsSelected()
        compose.runOnIdle { assertEquals(0L, VisionPreferences.controlsTimeoutMs) }
    }
}
