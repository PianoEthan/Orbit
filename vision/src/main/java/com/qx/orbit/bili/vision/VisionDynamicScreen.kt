package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreDynamic
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionDynamicScreen(onBack: () -> Unit, onLogin: () -> Unit, onVideo: (CoreVideo) -> Unit,
    viewModel: VisionDynamicViewModel = viewModel(), onImages: (List<String>, Int) -> Unit = { _, _ -> }) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val backFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(cookie) { viewModel.setAccount(cookie) }
    LaunchedEffect(inputMode.inputMode) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            backFocus.requestFocus()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::refresh, enabled = cookie.isNotBlank() && !state.loading) { Icon(Icons.Default.Refresh, "刷新动态") }
        }
        if (cookie.isBlank()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = onLogin) { Text("登录查看关注动态") }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxHeight(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        DynamicBody(item, onVideo, onImages)
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                    }
                    item(key = "status") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.error != null -> {
                                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    FilledTonalButton(onClick = viewModel::retry) { Text("重试动态") }
                                }
                                state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多动态") }
                                state.items.isEmpty() -> Text("暂无动态")
                                else -> Text("已加载全部动态", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicBody(item: CoreDynamic, onVideo: (CoreVideo) -> Unit, onImages: (List<String>, Int) -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(item.avatar, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Column {
                Text(item.author.ifBlank { "动态" }, style = MaterialTheme.typography.titleMedium)
                Text(item.published, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.text.isNotBlank()) {
            var overflows by remember(item.text) { mutableStateOf(false) }
            Text(item.text, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 6,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
            if (overflows || expanded) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起正文" else "展开正文") }
        }
        item.images.withIndex().toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (index, image) ->
                    VisionImageTile(image, "查看第 ${index + 1} 张动态图片", Modifier.weight(1f).aspectRatio(1f).sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { onImages(item.images, index) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item.video?.let { video -> VisionExpressiveVideoCard(video, onClick = { onVideo(video) }) }
        item.unsupportedType?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item.original?.let { original ->
            Text("转发原动态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DynamicBody(original, onVideo, onImages)
        }
    }
}
