package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
fun VisionDetailScreen(
    video: CoreVideo,
    onBack: () -> Unit,
    onPlay: (CoreVideo) -> Unit,
    viewModel: VisionDetailViewModel = viewModel(),
    onLogin: () -> Unit = {},
    onReplyThread: (Long, Long) -> Unit = { _, _ -> },
    onComposeReply: (Long, Long, Long) -> Unit = { _, _, _ -> },
    replyRevision: Int = 0,
    onCover: (String) -> Unit = {},
    onCreator: (Long) -> Unit = {},
    onVideo: (CoreVideo) -> Unit = onPlay,
    relatedModel: VisionRelatedViewModel = viewModel(),
) {
    val related by relatedModel.state.collectAsState()
    val details by viewModel.details.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedPage by viewModel.selectedPage.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val repliesLoading by viewModel.repliesLoading.collectAsState()
    val repliesError by viewModel.repliesError.collectAsState()
    val repliesHasMore by viewModel.repliesHasMore.collectAsState()
    var tab by rememberSaveable(video.bvid) { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    BackHandler(onBack = onBack)
    val cookie = VisionUserSession.cookie
    LaunchedEffect(cookie) { viewModel.setAccount(cookie) }
    LaunchedEffect(video.bvid) { viewModel.load(video) }
    LaunchedEffect(video.bvid, cookie) { relatedModel.load(video.bvid, cookie) }
    LaunchedEffect(replyRevision) { if (replyRevision > 0) viewModel.refreshReplies() }
    LaunchedEffect(video.bvid, inputMode.inputMode, loading) {
        if (!loading && details != null && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            playFocus.requestFocus()
        }
    }
    val current = details?.takeIf { it.video.bvid == video.bvid }
    val display = current?.video ?: video
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上一页")
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 840.dp && maxHeight >= 400.dp
                val windowWidth = maxWidth.value
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        VideoCover(display, Modifier.weight(0.9f).padding(start = 24.dp, end = 12.dp, top = 16.dp)) { onCover(display.cover) }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .padding(horizontal = if (wide) ((windowWidth - 560f).coerceAtLeast(0f) / 2).dp else 0.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!wide) item(key = "cover") { VideoCover(display, Modifier.fillMaxWidth()) { onCover(display.cover) } }
                        item(key = "information") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(display.title, style = MaterialTheme.typography.headlineSmall)
                                if ((current?.ownerMid ?: 0L) > 0L) {
                                    TextButton(onClick = { current?.ownerMid?.let(onCreator) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("${display.author} · 查看 UP 主空间", style = MaterialTheme.typography.titleMedium)
                                    }
                                } else Text(display.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                current?.let {
                                    Text("播放 ${it.viewCount} · 点赞 ${it.likeCount} · 评论 ${it.replyCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { viewModel.playbackVideo()?.let(onPlay) },
                                    enabled = current != null && !loading,
                                    modifier = Modifier.focusRequester(playFocus).heightIn(min = 56.dp),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    val resumeMs = viewModel.playbackVideo()?.startPositionMs ?: 0L
                                    Text(if (resumeMs > 0) "继续播放 ${resumeMs / 60000}:${(resumeMs / 1000 % 60).toString().padStart(2, '0')}" else selectedPage?.let { "播放 P${it.page}" } ?: "播放")
                                }
                                selectedPage?.takeIf { (current?.pages?.size ?: 0) > 1 }?.let {
                                    Text(it.part.ifBlank { "第 ${it.page} 集" }, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (current != null) VisionVideoActions(viewModel, onLogin)
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (error != null) {
                                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                    OutlinedButton(onClick = viewModel::retry) { Text("重新加载详情") }
                                }
                            }
                        }
                        item(key = "tabs") {
                            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.focusGroup()) {
                                listOf("简介", "选集 ${current?.pages?.size ?: 0}", "评论").forEachIndexed { index, label ->
                                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                                }
                            }
                        }
                        when (tab) {
                            0 -> {
                                item(key = "description") { Text(current?.description?.ifBlank { "暂无简介" } ?: "正在加载简介", style = MaterialTheme.typography.bodyLarge) }
                                if (!current?.collectionVideos.isNullOrEmpty()) {
                                    item(key = "collection-title") { Text("合集 · ${current?.collectionTitle.orEmpty()}", style = MaterialTheme.typography.titleLarge) }
                                    items(current?.collectionVideos.orEmpty(), key = { "collection-${it.bvid}" }) { item ->
                                        FilledTonalButton(onClick = { onVideo(item) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(item.title) }
                                    }
                                }
                                item(key = "related-title") { Text("相关推荐", style = MaterialTheme.typography.titleLarge) }
                                related.videos.chunked(if (wide) 2 else 1).forEachIndexed { rowIndex, row ->
                                    item(key = "related-row-$rowIndex") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            row.forEach { item ->
                                                VisionExpressiveVideoCard(item, modifier = Modifier.weight(1f), onClick = { onVideo(item) })
                                            }
                                            if (wide && row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                item(key = "related-status") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (related.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        related.error?.let {
                                            Text(it, color = MaterialTheme.colorScheme.error)
                                            FilledTonalButton(onClick = { relatedModel.load(video.bvid, cookie) }) { Text("重试相关推荐") }
                                        }
                                        if (!related.loading && related.error == null && related.videos.isEmpty()) Text("暂无相关推荐")
                                    }
                                }
                            }
                            1 -> {
                                if (current?.pages.isNullOrEmpty() && !loading) item { Text("暂无选集") }
                                items(current?.pages.orEmpty(), key = { "page-${it.cid}" }) { page ->
                                    val interaction = remember { MutableInteractionSource() }
                                    val focused by interaction.collectIsFocusedAsState()
                                    val isSelected = selectedPage?.cid == page.cid
                                    Surface(
                                        onClick = { viewModel.selectPage(page) }, interactionSource = interaction,
                                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
                                        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected; role = Role.RadioButton },
                                    ) {
                                        Row(Modifier.padding(16.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("P${page.page}", style = MaterialTheme.typography.labelLarge)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(page.part.ifBlank { "第 ${page.page} 集" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text("${page.durationSeconds / 60}:${(page.durationSeconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (isSelected) Icon(Icons.Default.Check, contentDescription = "当前选集")
                                        }
                                    }
                                }
                            }
                            2 -> {
                                item(key = "compose-reply") {
                                    FilledTonalButton(onClick = { current?.video?.id?.let { onComposeReply(it, 0, 0) } }, enabled = current != null) { Text("写评论") }
                                }
                                if (!repliesLoading && repliesError == null && replies.isEmpty() && !repliesHasMore) item { Text("暂无评论") }
                                items(replies, key = { "reply-${it.rpid}" }) { reply ->
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (reply.pinned) Text("置顶评论", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            AsyncImage(reply.avatar, null, Modifier.size(36.dp).clip(CircleShape))
                                            Text(reply.uname, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Text(reply.message, style = MaterialTheme.typography.bodyLarge)
                                        Text("${reply.likeCount} 赞", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { current?.video?.id?.let { onComposeReply(it, reply.rpid, reply.rpid) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("回复") }
                                            if (reply.replyCount > 0) TextButton(onClick = { current?.video?.id?.let { onReplyThread(it, reply.rpid) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                                Text("查看 ${reply.replyCount} 条回复")
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                                item(key = "replies-status") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when {
                                            repliesLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                            repliesError != null -> {
                                                Text(repliesError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                                FilledTonalButton(onClick = viewModel::retryReplies) { Text("重试评论") }
                                            }
                                            repliesHasMore -> FilledTonalButton(onClick = viewModel::loadMoreReplies) { Text("加载更多评论") }
                                            replies.isNotEmpty() -> Text("已加载全部评论", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCover(video: CoreVideo, modifier: Modifier, onClick: () -> Unit) {
    VisionImageTile(video.cover, "查看视频封面", modifier.aspectRatio(16f / 9f), onClick)
}
