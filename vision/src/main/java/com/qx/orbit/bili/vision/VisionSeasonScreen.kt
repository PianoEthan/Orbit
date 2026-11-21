package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionSeasonCatalogScreen(onBack: () -> Unit, onSeason: (Long) -> Unit, onLogin: () -> Unit,
    viewModel: VisionSeasonViewModel = viewModel()) {
    var type by rememberSaveable { mutableIntStateOf(1) }
    var followed by rememberSaveable { mutableStateOf(false) }
    val cookie = VisionUserSession.cookie
    val state by viewModel.state.collectAsState()
    LaunchedEffect(type, followed, cookie) { viewModel.openCatalog(type, followed, VisionUserSession.mid, cookie) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            TextButton(onClick = { followed = !followed; if (type !in 1..2) type = 1 }) { Text(if (followed) "查看全部剧集" else "我的追番追剧") }
        }
        val types = if (followed) listOf(1 to "追番", 2 to "追剧") else listOf(1 to "番剧", 4 to "国创", 2 to "电影", 5 to "电视剧", 3 to "纪录片", 7 to "综艺")
        PrimaryScrollableTabRow(selectedTabIndex = types.indexOfFirst { it.first == type }.coerceAtLeast(0)) {
            types.forEach { (value, title) -> Tab(type == value, onClick = { type = value }, text = { Text(title) }) }
        }
        if (followed && !VisionUserSession.isLoggedIn) {
            Button(onClick = onLogin, modifier = Modifier.padding(24.dp)) { Text("登录查看追番追剧") }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(columns = GridCells.Fixed(visionGridColumns(maxWidth.value)), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.items.size, key = { state.items[it].id }) { index ->
                    val season = state.items[index]
                    Card(onClick = { onSeason(season.id) }, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(season.cover, null, Modifier.fillMaxWidth().aspectRatio(0.7f), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(season.title, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(season.subtitle, season.badge).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.error != null -> { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error); FilledTonalButton(onClick = viewModel::retry) { Text("重试剧集列表") } }
                            state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多剧集") }
                            state.items.isEmpty() -> Text("暂无剧集")
                            else -> Text("已加载全部剧集")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisionSeasonDetailScreen(seasonId: Long, onBack: () -> Unit, onPlay: (CoreVideo) -> Unit, onSeason: (Long) -> Unit,
    onLogin: () -> Unit, viewModel: VisionSeasonViewModel = viewModel()) {
    val cookie = VisionUserSession.cookie
    val state by viewModel.state.collectAsState()
    LaunchedEffect(seasonId, cookie) { viewModel.openDetail(seasonId, cookie) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.detail?.let { detail ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(detail.season.cover, null, Modifier.height(240.dp).aspectRatio(0.7f), contentScale = ContentScale.Crop)
                        Text(detail.season.title, style = MaterialTheme.typography.headlineMedium)
                        Text(detail.description)
                        val resume = detail.episodes.firstOrNull { it.id == detail.lastEpisodeId } ?: detail.episodes.firstOrNull()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { resume?.video?.let(onPlay) }, enabled = resume != null) { Text(if (detail.lastEpisodeId > 0) "继续观看" else "开始观看") }
                            FilledTonalButton(onClick = { if (!VisionUserSession.isLoggedIn) onLogin() else viewModel.changeFollow(VisionUserSession.csrf) }, enabled = !state.pending) {
                                Text(if (detail.followed) "已追剧 · 取消" else "追剧")
                            }
                            TextButton(onClick = viewModel::retry, enabled = !state.pending && !state.loading) { Text("刷新剧集资料") }
                        }
                        state.message?.let { Text(it) }
                        if (state.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (detail.seasons.size > 1) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.seasons.forEach { season -> FilterChip(selected = season.id == seasonId, onClick = { onSeason(season.id) }, label = { Text(season.title) }) }
                    }
                }
                item { Text("剧集与花絮", style = MaterialTheme.typography.titleLarge) }
                items(detail.episodes, key = { it.id }) { episode ->
                    FilledTonalButton(onClick = { onPlay(episode.video) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(listOf(episode.title, episode.badge).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                }
            }
            item {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); FilledTonalButton(onClick = viewModel::retry) { Text("重试剧集详情") } }
            }
        }
    }
}
