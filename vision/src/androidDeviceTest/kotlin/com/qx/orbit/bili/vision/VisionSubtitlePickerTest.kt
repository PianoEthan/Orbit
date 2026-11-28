package com.qx.orbit.bili.vision

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.CoreSubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisionSubtitlePickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun subtitleLanguageSelectionAndDisableAreAccessible() {
        val track = CoreSubtitleTrack(1, "zh-CN", "中文", "https://aisubtitle.hdslb.com/test", false)
        var selected: CoreSubtitleTrack? = null
        compose.setContent {
            var state by remember { mutableStateOf(VisionSubtitleState(tracks = listOf(track))) }
            VisionTheme { VisionSubtitlePicker(state, onSelect = { selected = it; state = state.copy(selected = it) }, onRetry = {}, onDismiss = {}) }
        }
        compose.onNodeWithText("中文").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
        compose.onNodeWithText("✓ 中文").assertIsDisplayed()
        compose.onNodeWithText("关闭字幕").performClick()
        compose.runOnIdle { assertEquals(null, selected) }
    }
}
