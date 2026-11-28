package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisionNineKeyKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmOpensInlineLayerAndDirectionSelectsOneLetterThenRestoresFocus() {
        var typed = ""
        compose.setContent {
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
            VisionTheme { VisionNineKeyKeyboard({ typed += it }, {}, {}, true, Modifier.fillMaxWidth()) }
        }
        compose.onNodeWithText("ABC").performClick()
        compose.onNodeWithText("按方向键选字母: ABC").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals("B", typed) }
        compose.onNodeWithText("按方向键选字母: ABC").assertDoesNotExist()
        compose.onNodeWithText("ABC").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("按方向键选字母: ABC").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals("BA", typed) }
    }

    @Test fun systemBackOnlyDismissesOverlayAndMissingFourthLetterDoesNotType() {
        var exits = 0
        var typed = ""
        compose.setContent {
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
            BackHandler { exits++ }
            VisionTheme { VisionNineKeyKeyboard({ typed += it }, {}, {}, true, Modifier.fillMaxWidth()) }
        }
        compose.onNodeWithText("DEF").performClick()
        Espresso.pressBack()
        compose.onNodeWithText("按方向键选字母: DEF").assertDoesNotExist()
        compose.onNodeWithText("DEF").assertIsFocused()
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals("", typed) }
        compose.onNodeWithText("DEF").assertIsFocused()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun numericModeSupportsZeroDeleteAndSearch() {
        var searches = 0
        var value = ""
        compose.setContent {
            var text by remember { mutableStateOf("") }
            VisionTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("输入:$text")
                    VisionNineKeyKeyboard({ text += it; value = text }, { text = deleteSearchCharacter(text); value = text }, { searches++ }, text.isNotBlank())
                }
            }
        }
        compose.onNodeWithText("123").performClick()
        compose.onNodeWithText("9").performClick()
        compose.onNodeWithText("0").performClick()
        compose.onNodeWithContentDescription("退格").performClick()
        compose.runOnIdle { assertEquals("9", value) }
        compose.onNodeWithText("搜索").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, searches) }
    }

    @Test fun activeLetterGroupSurvivesSavedStateRestoration() {
        val restore = StateRestorationTester(compose)
        restore.setContent { VisionTheme { VisionNineKeyKeyboard({}, {}, {}, false) } }
        compose.onNodeWithText("WXYZ").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("按方向键选字母: WXYZ").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("按方向键选字母: WXYZ").assertDoesNotExist()
    }
}
