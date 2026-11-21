package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionCreatorScreen(mid: Long, onBack: () -> Unit, onLogin: () -> Unit, onVideo: (CoreVideo) -> Unit,
    onFollowList: (VisionFollowMode) -> Unit = {},
    viewModel: VisionCreatorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val backFocus = remember { FocusRequester() }
    VisionInitialFocus(backFocus)
    var confirmBlock by remember(mid, cookie) { mutableStateOf(false) }
    LaunchedEffect(mid, cookie) { viewModel.open(mid, cookie) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).focusRequester(backFocus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(columns = GridCells.Fixed(visionGridColumns(maxWidth.value)), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.creator?.let { creator ->
                            AsyncImage(creator.avatar, null, Modifier.size(72.dp).clip(CircleShape))
                            Text(creator.name, style = MaterialTheme.typography.headlineMedium)
                            Text(creator.signature.ifBlank { "暂无签名" })
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                TextButton(
                                    onClick = { onFollowList(VisionFollowMode.FANS) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.heightIn(min = visionTouchMinHeight()),
                                ) {
                                    Text("${creator.fans} 粉丝", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = { onFollowList(VisionFollowMode.FOLLOWING) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.heightIn(min = visionTouchMinHeight()),
                                ) {
                                    Text("${creator.following} 关注", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (mid != VisionUserSession.mid) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = {
                                    if (!VisionUserSession.isLoggedIn) onLogin()
                                    else viewModel.changeRelation(if (creator.followed) 2 else 1, VisionUserSession.csrf)
                                }, enabled = !state.pending && !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(if (creator.followed) "已关注 · 取消关注" else "关注 UP 主")
                                }
                                if (VisionUserSession.isLoggedIn) TextButton(onClick = { confirmBlock = true }, enabled = !state.pending && !state.loading) { Text("拉黑用户") }
                            }
                        }
                        if (state.loading || state.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.message?.let { Text(it) }
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = viewModel::refreshProfile) { Text("重试资料") }
                        }
                        Text("视频投稿", style = MaterialTheme.typography.titleLarge)
                    }
                }
                items(state.videos, key = { it.bvid }) { video -> VisionExpressiveVideoCard(video, onClick = { onVideo(video) }) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            state.videosLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.videosError != null -> {
                                Text(state.videosError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                FilledTonalButton(onClick = viewModel::loadVideos) { Text("重试投稿") }
                            }
                            state.hasMore -> FilledTonalButton(onClick = viewModel::loadVideos) { Text("加载更多投稿") }
                            state.videos.isEmpty() -> Text("暂无公开投稿")
                            else -> Text("已加载全部投稿")
                        }
                    }
                }
            }
        }
    }
    if (confirmBlock) AlertDialog(onDismissRequest = { confirmBlock = false }, title = { Text("拉黑该用户？") },
        text = { Text("将使用当前账号拉黑 ${state.creator?.name.orEmpty()}。") },
        confirmButton = { TextButton(onClick = { confirmBlock = false; viewModel.changeRelation(5, VisionUserSession.csrf) }) { Text("确认拉黑") } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("取消") } })
}
