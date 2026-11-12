package com.qx.orbit.bili.vision

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun VisionVideoViewport(viewport: VideoViewport, zoomed: Boolean, enabled: Boolean, content: @Composable (Modifier) -> Unit) {
    var scale by remember(viewport) { mutableFloatStateOf(1f) }
    var panX by remember(viewport) { mutableFloatStateOf(0f) }
    var panY by remember(viewport) { mutableFloatStateOf(0f) }
    LaunchedEffect(zoomed, enabled) { scale = if (zoomed && enabled) 2f else 1f; panX = 0f; panY = 0f }
    Box(Modifier.size(viewport.width.dp, viewport.height.dp).clipToBounds()) {
        content(Modifier.size(viewport.width.dp, viewport.height.dp).graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = panX; translationY = panY
        }.pointerInput(enabled) {
            if (enabled) detectTransformGestures { _, pan, factor, _ ->
                scale = (scale * factor).coerceIn(1f, 4f)
                panX = visionImagePan(panX + pan.x, size.width.toFloat(), scale)
                panY = visionImagePan(panY + pan.y, size.height.toFloat(), scale)
            }
        })
    }
}
