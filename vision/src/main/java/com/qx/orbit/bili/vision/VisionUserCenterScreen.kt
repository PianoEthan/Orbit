package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreUserProfile
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionUserCenterScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onVideoClick: (CoreVideo) -> Unit,
    viewModel: VisionUserCenterViewModel = viewModel(),
    onOpenDynamics: () -> Unit = {},
) {
    val context = LocalContext.current
    val isLoggedIn = VisionUserSession.isLoggedIn
    val profile = VisionUserSession.profile
    val cookie = VisionUserSession.cookie
    val activeTab by viewModel.tab.collectAsState()
    val history by viewModel.history.collectAsState()
    val historyHasMore by viewModel.historyHasMore.collectAsState()
    val watchLater by viewModel.watchLater.collectAsState()
    val favVideos by viewModel.favVideos.collectAsState()
    val folders by viewModel.favFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val favHasMore by viewModel.favHasMore.collectAsState()
    var choosingFolder by remember { mutableStateOf(false) }
    var restoreFolderFocus by remember { mutableStateOf(false) }
    val folderFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val dynamicVideos by viewModel.dynamicVideos.collectAsState()
    val dynamicHasMore by viewModel.dynamicHasMore.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val removing by viewModel.removing.collectAsState()
    val mutationMessage by viewModel.mutationMessage.collectAsState()
    BackHandler(onBack = onBack)
    LaunchedEffect(cookie) {
        choosingFolder = false
        restoreFolderFocus = false
        viewModel.onSessionChanged()
    }
    LaunchedEffect(choosingFolder, restoreFolderFocus) {
        if (!choosingFolder && restoreFolderFocus && activeTab == UserCenterTab.FAVORITES && folders.isNotEmpty() && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            folderFocus.requestFocus()
            restoreFolderFocus = false
        }
    }
    if (choosingFolder) VisionUserFolderPicker(folders, selectedFolder,
        onSelect = { choosingFolder = false; viewModel.selectFolder(it) }, onDismiss = { choosingFolder = false })

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Spacer(Modifier.weight(1f))
                if (isLoggedIn) IconButton(onClick = viewModel::refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新数据") }
            }
            if (!isLoggedIn) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                    Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(if (VisionUserSession.sessionExpired) "登录已失效，请重新扫码" else "未登录哔哩哔哩账号", style = MaterialTheme.typography.headlineSmall)
                        Text("登录后即可同步历史记录、稍后再看、我的收藏与动态更新。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onNavigateToLogin, modifier = Modifier.heightIn(min = 56.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Login, null)
                            Spacer(Modifier.width(8.dp))
                            Text("立即扫码登录")
                        }
                    }
                }
            } else {
                VisionUserTabs(activeTab, viewModel::selectTab)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = visionUserGridColumns(maxWidth.value - 40, LocalDensity.current.fontScale)
                    val currentVideos = when (activeTab) {
                        UserCenterTab.HISTORY -> history.map {
                            CoreVideo(it.aid, it.bvid, it.title, it.cover, it.author, cid = it.cid,
                                startPositionMs = resumeHistoryPosition(it.progressSeconds, it.durationSeconds),
                                durationText = if (it.durationSeconds > 0) "${it.durationSeconds / 60}:${(it.durationSeconds % 60).toString().padStart(2, '0')}" else "",
                                viewText = when {
                                    it.progressPercent >= 1f -> "已看完"
                                    it.progressPercent > 0 -> "已看 ${(it.progressPercent * 100).toInt()}%"
                                    else -> "刚刚看过"
                                })
                        }
                        UserCenterTab.WATCH_LATER -> watchLater
                        UserCenterTab.FAVORITES -> favVideos
                        UserCenterTab.DYNAMIC -> dynamicVideos
                    }
                    val scrollStates = key(cookie) { rememberSaveableStateHolder() }
                    scrollStates.SaveableStateProvider(activeTab.name) {
                        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            item(key = "account", span = { GridItemSpan(maxLineSpan) }) {
                                VisionUserProfileHeader(profile, VisionUserSession.mid, onLogout = { VisionUserSession.logout(context) })
                            }
                            if (activeTab == UserCenterTab.DYNAMIC) item(key = "all-dynamics", span = { GridItemSpan(maxLineSpan) }) {
                                TextButton(onClick = onOpenDynamics, modifier = Modifier.heightIn(min = 48.dp)) { Text("查看文字、图片与转发动态") }
                            }
                            if (activeTab == UserCenterTab.WATCH_LATER && mutationMessage != null) item(key = "mutation", span = { GridItemSpan(maxLineSpan) }) {
                                Text(mutationMessage.orEmpty())
                            }
                            if (activeTab == UserCenterTab.FAVORITES && folders.isNotEmpty()) item(key = "folder", span = { GridItemSpan(maxLineSpan) }) {
                                FilledTonalButton(onClick = { restoreFolderFocus = true; choosingFolder = true }, modifier = Modifier.focusRequester(folderFocus).heightIn(min = 56.dp)) {
                                    val folder = folders.firstOrNull { it.id == selectedFolder }
                                    Text("${folder?.title.orEmpty()} · ${folder?.mediaCount ?: 0} 个视频 · 切换收藏夹")
                                }
                            }
                            items(currentVideos, key = { "${it.bvid}:${it.cid}" }) { item ->
                                Column {
                                    VisionExpressiveVideoCard(item, onClick = { onVideoClick(item) })
                                    if (activeTab == UserCenterTab.WATCH_LATER) TextButton(onClick = { viewModel.removeWatchLater(item) }, enabled = removing == null && !loading,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (removing == item.id) "正在移除…" else "从稍后再看移除") }
                                }
                            }
                            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    when {
                                        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                        error != null -> {
                                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                            FilledTonalButton(onClick = viewModel::retry) { Text("重试") }
                                        }
                                        activeTab == UserCenterTab.DYNAMIC && dynamicHasMore -> FilledTonalButton(onClick = viewModel::loadMoreDynamic) { Text("加载更多动态视频") }
                                        activeTab == UserCenterTab.HISTORY && historyHasMore -> FilledTonalButton(onClick = viewModel::loadMoreHistory) { Text("加载更多历史") }
                                        activeTab == UserCenterTab.FAVORITES && favHasMore -> FilledTonalButton(onClick = viewModel::loadMoreFavorites) { Text("加载更多收藏") }
                                        currentVideos.isEmpty() -> Text("暂无${activeTab.title}内容")
                                        else -> Text("已加载全部${activeTab.title}", style = MaterialTheme.typography.bodyMedium)
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
internal fun VisionUserProfileHeader(profile: CoreUserProfile?, mid: Long, onLogout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (profile?.face?.isNotBlank() == true) AsyncImage(profile.face, null, Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.AccountCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile?.uname?.ifBlank { "已登录用户" } ?: "已登录用户", style = MaterialTheme.typography.titleLarge)
                Text("UID: $mid", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(profile?.let { "LV${it.level} · 硬币 ${it.coins}" } ?: "正在获取账号资料", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLogout, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }
        }
    }
}
