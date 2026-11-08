package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun VisionImageScreen(urls: List<String>, initialPage: Int, onBack: () -> Unit) {
    val sources = remember(urls) { visionImageSources(urls) }
    var page by rememberSaveable(sources) { mutableIntStateOf(visionImagePage(initialPage, 0, sources.size)) }
    var scale by rememberSaveable(page) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(page) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(page) { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(page) { mutableStateOf(false) }
    var loading by remember(page) { mutableStateOf(true) }
    var retry by remember(page) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val inputMode = LocalInputModeManager.current
    val zoomFocus = remember { FocusRequester() }
    fun zoom(target: Float) {
        scale = target.coerceIn(1f, 4f)
        panX = visionImagePan(panX, viewport.width.toFloat(), scale)
        panY = visionImagePan(panY, viewport.height.toFloat(), scale)
    }
    BackHandler { if (scale > 1f) zoom(1f) else onBack() }
    LaunchedEffect(inputMode.inputMode) {
        if (inputMode.inputMode == InputMode.Keyboard && sources.isNotEmpty()) {
            withFrameNanos { }
            zoomFocus.requestFocus()
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).onPreviewKeyEvent { event ->
        if (event.key !in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent scale > 1f || event.key == Key.DirectionLeft || event.key == Key.DirectionRight
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
            scale > 1f -> {
                val delta = 80f
                when (event.key) {
                    Key.DirectionLeft -> panX = visionImagePan(panX + delta, viewport.width.toFloat(), scale)
                    Key.DirectionRight -> panX = visionImagePan(panX - delta, viewport.width.toFloat(), scale)
                    Key.DirectionUp -> panY = visionImagePan(panY + delta, viewport.height.toFloat(), scale)
                    Key.DirectionDown -> panY = visionImagePan(panY - delta, viewport.height.toFloat(), scale)
                }
                true
            }
            event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
                page = visionImagePage(page, if (event.key == Key.DirectionLeft) -1 else 1, sources.size)
                true
            }
            else -> false
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(Modifier.weight(1f))
            Text(if (sources.isEmpty()) "没有可查看的图片" else "${page + 1} / ${sources.size}", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(16.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }, contentAlignment = Alignment.Center) {
            sources.getOrNull(page)?.let { url ->
                key(url, retry) {
                    AsyncImage(model = ImageRequest.Builder(context).data(url).setHeader("Referer", "https://www.bilibili.com/").setParameter("retry", retry).build(),
                        contentDescription = "第 ${page + 1} 张图片，共 ${sources.size} 张", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = panX; translationY = panY }
                            .pointerInput(page) { detectTapGestures(onDoubleTap = { zoom(if (scale > 1f) 1f else 2f) }) }
                            .pointerInput(page) { detectTransformGestures { _, pan, factor, _ ->
                                zoom(scale * factor)
                                panX = visionImagePan(panX + pan.x, viewport.width.toFloat(), scale)
                                panY = visionImagePan(panY + pan.y, viewport.height.toFloat(), scale)
                            } },
                        onLoading = { loading = true; failed = false }, onSuccess = { loading = false; failed = false }, onError = { loading = false; failed = true })
                }
            }
            if (loading && sources.isNotEmpty()) CircularProgressIndicator()
            if (failed) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("图片加载失败", style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick = { failed = false; loading = true; retry++ }) { Text("重试图片") }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (scale > 1f) "拖动或方向键移动 · 返回键还原" else "左右键翻图 · 双击或捏合缩放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { page = visionImagePage(page, -1, sources.size) }, enabled = page > 0) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "上一张") }
                Button(onClick = { zoom(if (scale > 1f) 1f else 2f) }, enabled = sources.isNotEmpty(), modifier = Modifier.focusRequester(zoomFocus).heightIn(min = 48.dp)) { Text(if (scale > 1f) "还原图片" else "放大图片") }
                FilledTonalIconButton(onClick = { page = visionImagePage(page, 1, sources.size) }, enabled = page < sources.lastIndex) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "下一张") }
            }
        }
    }
}
