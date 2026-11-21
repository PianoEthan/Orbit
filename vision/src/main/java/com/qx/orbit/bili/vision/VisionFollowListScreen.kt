package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.core.api.CoreFollowUser

@Composable
internal fun VisionFollowListScreen(
    mid: Long,
    mode: VisionFollowMode,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: VisionFollowListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val isSelf = mid == VisionUserSession.mid
    BackHandler(onBack = onBack)
    LaunchedEffect(mid, mode, cookie) { viewModel.open(mid, mode, cookie) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(
                    if (mode == VisionFollowMode.FOLLOWING) "关注列表" else "粉丝列表",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (state.total > 0) {
                    Text("${state.total} 人", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading && state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.error != null && state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::retry) { Text("重试") }
                    }
                }
            } else if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无${if (mode == VisionFollowMode.FOLLOWING) "关注" else "粉丝"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.mid }) { user ->
                        VisionFollowUserRow(
                            user = user,
                            showRelation = !isSelf,
                            busy = state.relationBusy == user.mid,
                            onClick = { onOpenUser(user.mid) },
                            onToggleFollow = {
                                viewModel.toggleFollow(user.mid, user.attribute != 2 && user.attribute != 3, VisionUserSession.csrf)
                            },
                        )
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            state.relationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                state.error != null -> FilledTonalButton(onClick = viewModel::retry) { Text("重试加载更多") }
                                state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多") }
                                else -> Text("已加载全部内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        layout.totalItemsCount > 0 && layout.visibleItemsInfo.lastOrNull()?.index == layout.totalItemsCount - 1
                    }.collect { atEnd -> if (atEnd) viewModel.loadMore() }
                }
            }
        }
    }
}

@Composable
private fun VisionFollowUserRow(
    user: CoreFollowUser,
    showRelation: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val context = LocalContext.current
    val following = user.attribute == 2 || user.attribute == 3
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = remember(user.face, context) {
                    if (user.face.isBlank()) null else ImageRequest.Builder(context)
                        .data(user.face).size(160)
                        .setHeader("Referer", "https://www.bilibili.com/").crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (user.sign.isNotBlank()) {
                    Text(
                        user.sign,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showRelation) {
                if (following) {
                    OutlinedButton(
                        onClick = onToggleFollow,
                        enabled = !busy,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("已关注", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onToggleFollow,
                        enabled = !busy,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("关注", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
