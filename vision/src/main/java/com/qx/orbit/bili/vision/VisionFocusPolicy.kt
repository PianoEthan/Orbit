package com.qx.orbit.bili.vision

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.withFrameNanos

@Composable
internal fun VisionInitialFocus(focus: FocusRequester, vararg keys: Any?) {
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(inputMode.inputMode, *keys) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}
