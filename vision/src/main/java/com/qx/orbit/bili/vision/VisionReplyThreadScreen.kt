package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreVideoReply

@Composable
internal fun VisionReplyThreadScreen(aid: Long, root: Long, onBack: () -> Unit,
    viewModel: VisionReplyThreadViewModel = viewModel(), onComposeReply: (Long) -> Unit = {}, replyRevision: Int = 0) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val backFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(aid, root, cookie, replyRevision) {
        viewModel.open(aid, root, cookie)
        if (replyRevision > 0) viewModel.refresh()
    }
    LaunchedEffect(inputMode.inputMode) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            backFocus.requestFocus()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus).padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回评论区") }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxHeight(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                state.root?.let { parent ->
                    item(key = "root") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("原评论", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            ReplyThreadItem(parent) { onComposeReply(root) }
                        }
                    }
                }
                item(key = "section") { Text("评论回复", style = MaterialTheme.typography.titleMedium) }
                items(state.replies, key = { it.rpid }) { reply -> ReplyThreadItem(reply) { onComposeReply(reply.rpid) } }
                item(key = "status") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.error != null -> {
                                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                FilledTonalButton(onClick = viewModel::retry) { Text("重试回复") }
                            }
                            state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多回复") }
                            state.replies.isEmpty() -> Text("暂无回复")
                            else -> Text("已加载全部回复", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyThreadItem(reply: CoreVideoReply, onReply: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(reply.avatar, null, Modifier.size(36.dp).clip(CircleShape))
            Text(reply.uname, style = MaterialTheme.typography.titleSmall)
        }
        Text(reply.message, style = MaterialTheme.typography.bodyLarge)
        Text("${reply.likeCount} 赞", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReply, modifier = Modifier.heightIn(min = 48.dp)) { Text("回复") }
        HorizontalDivider()
    }
}
