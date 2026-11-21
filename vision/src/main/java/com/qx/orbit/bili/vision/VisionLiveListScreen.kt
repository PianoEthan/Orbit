package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CoreLiveRoom

@Composable
internal fun VisionLiveListScreen(
    onBack: () -> Unit,
    onRoomClick: (Long) -> Unit,
    viewModel: VisionLiveListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isLoggedIn = VisionUserSession.isLoggedIn
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { if (state.rooms.isEmpty()) viewModel.selectFeed(state.feed) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("直播", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.feed == VisionLiveFeed.RECOMMEND,
                    onClick = { viewModel.selectFeed(VisionLiveFeed.RECOMMEND) },
                    label = { Text("推荐直播") },
                )
                FilterChip(
                    selected = state.feed == VisionLiveFeed.FOLLOWED,
                    onClick = { if (isLoggedIn) viewModel.selectFeed(VisionLiveFeed.FOLLOWED) },
                    enabled = isLoggedIn,
                    label = { Text(if (isLoggedIn) "关注的主播" else "关注主播需登录") },
                )
            }
            if (state.rooms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        state.error != null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::retry) { Text("重试") }
                        }
                        else -> Text("暂无直播", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else BoxWithConstraints(Modifier.fillMaxSize()) {
                val roomRow: @Composable (CoreLiveRoom) -> Unit = { room ->
                    SearchResultRow(
                        imageUrl = room.cover.ifBlank { room.face },
                        title = room.title.ifBlank { room.uname },
                        subtitle = buildString {
                            append(room.uname)
                            if (room.areaName.isNotBlank()) append(" · ").append(room.areaName)
                        },
                        trailing = if (room.liveStatus == 1) "直播中 · ${formatVisionOnline(room.online)}" else "已下播",
                        live = room.liveStatus == 1,
                        onClick = { onRoomClick(room.roomId) },
                    )
                }
                val footer: @Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多") }
                            else -> Text("已加载全部内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val columns = visionListColumns(maxWidth.value)
                if (columns == 1) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rooms, key = { it.roomId }) { room -> roomRow(room) }
                        item { footer() }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rooms, key = { it.roomId }) { room -> roomRow(room) }
                        item(span = { GridItemSpan(maxLineSpan) }) { footer() }
                    }
                }
            }
        }
    }
}
