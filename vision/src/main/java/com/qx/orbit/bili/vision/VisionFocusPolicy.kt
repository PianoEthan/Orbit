package com.qx.orbit.bili.vision

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
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

@Composable
internal fun Modifier.visionFocusBorder(shape: Shape = MaterialTheme.shapes.small): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
}

@Composable
internal fun visionTouchMinHeight(): androidx.compose.ui.unit.Dp =
    if (LocalInputModeManager.current.inputMode == InputMode.Keyboard) 48.dp else 40.dp
