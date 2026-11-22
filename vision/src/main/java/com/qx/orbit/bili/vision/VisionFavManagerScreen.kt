package com.qx.orbit.bili.vision

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CoreFavFolder

@Composable
internal fun VisionFavManagerScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: VisionFavManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val mid = VisionUserSession.mid
    val backFocus = remember { FocusRequester() }
    VisionInitialFocus(backFocus, state.folders.size, state.mode)
    var createOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<CoreFavFolder?>(null) }
    var deleteTarget by remember { mutableStateOf<CoreFavFolder?>(null) }
    var choosingTarget by remember { mutableStateOf(false) }
    BackHandler { if (state.mode == FavManagerMode.MOVE) viewModel.backToFolders() else onBack() }
    LaunchedEffect(cookie, mid) { viewModel.load(cookie, mid) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    if (choosingTarget) {
        val source = state.moveSource
        VisionUserFolderPicker(state.folders.filter { it.id != source?.id }, state.moveTarget,
            onSelect = { viewModel.setTarget(it); choosingTarget = false }, onDismiss = { choosingTarget = false })
    }
    if (createOpen) FolderEditorDialog(null, state.busy, onDismiss = { createOpen = false },
        onConfirm = { title, intro, privacy -> createOpen = false; viewModel.create(title, intro, privacy, VisionUserSession.csrf) })
    editTarget?.let { folder ->
        FolderEditorDialog(folder, state.busy, onDismiss = { editTarget = null },
            onConfirm = { title, intro, privacy -> editTarget = null; viewModel.edit(folder.id, title, intro, privacy, VisionUserSession.csrf) })
    }
    deleteTarget?.let { folder ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除收藏夹？") },
            text = { Text("将删除「${folder.title}」及其中的收藏记录，此操作不可撤回。") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; viewModel.delete(folder.id, VisionUserSession.csrf) }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (state.mode == FavManagerMode.MOVE) viewModel.backToFolders() else onBack() }, modifier = Modifier.focusRequester(backFocus)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text(if (state.mode == FavManagerMode.MOVE) "移动视频" else "收藏夹管理", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                if (state.mode == FavManagerMode.FOLDERS && VisionUserSession.isLoggedIn) {
                    TextButton(onClick = { createOpen = true }, enabled = !state.busy, modifier = Modifier.heightIn(min = visionTouchMinHeight())) { Text("新建收藏夹") }
                }
            }
            if (!VisionUserSession.isLoggedIn) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = onLogin) { Text("登录后管理收藏夹") }
                }
            } else if (state.mode == FavManagerMode.MOVE) {
                MoveContent(state, viewModel, onChooseTarget = { choosingTarget = true })
            } else {
                FolderList(state, viewModel, onEdit = { editTarget = it }, onDelete = { deleteTarget = it }, onMove = { viewModel.startMove(it) })
            }
        }
    }
}

@Composable
private fun ColumnScope.FolderList(
    state: VisionFavManagerState,
    viewModel: VisionFavManagerViewModel,
    onEdit: (CoreFavFolder) -> Unit,
    onDelete: (CoreFavFolder) -> Unit,
    onMove: (CoreFavFolder) -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxHeight(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            when {
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        FilledTonalButton(onClick = { viewModel.load(VisionUserSession.cookie, VisionUserSession.mid) }) { Text("重试") }
                    }
                }
                state.folders.isEmpty() -> item { Text("暂无收藏夹，点击右上角新建") }
                else -> items(state.folders, key = { it.id }) { folder ->
                    Surface(Modifier.fillMaxWidth().visionFocusBorder(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(folder.title, style = MaterialTheme.typography.titleSmall)
                                Text("${folder.mediaCount} 个视频", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onMove(folder) }, enabled = !state.busy, modifier = Modifier.heightIn(min = visionTouchMinHeight())) { Text("移动视频") }
                            TextButton(onClick = { onEdit(folder) }, enabled = !state.busy, modifier = Modifier.heightIn(min = visionTouchMinHeight())) { Text("重命名") }
                            TextButton(onClick = { onDelete(folder) }, enabled = !state.busy, modifier = Modifier.heightIn(min = visionTouchMinHeight())) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MoveContent(state: VisionFavManagerState, viewModel: VisionFavManagerViewModel, onChooseTarget: () -> Unit) {
    val source = state.moveSource ?: return
    val targetName = state.folders.firstOrNull { it.id == state.moveTarget }?.title
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("从「${source.title}」移动到", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onChooseTarget, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) { Text(targetName ?: "选择目标收藏夹") }
            Button(onClick = { viewModel.move(VisionUserSession.csrf) },
                enabled = !state.busy && state.moveSelected.isNotEmpty() && state.moveTarget != null && state.moveTarget != source.id,
                modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) {
                Text(if (state.busy) "正在移动…" else "移动 ${state.moveSelected.size} 个")
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (state.moveLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.moveVideos, key = { it.id }) { video ->
                val selected = video.id in state.moveSelected
                Surface(onClick = { viewModel.toggle(video.id) }, modifier = Modifier.fillMaxWidth().visionFocusBorder(),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(checked = selected, onCheckedChange = { viewModel.toggle(video.id) })
                        Text(video.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!state.moveLoading && state.moveVideos.isEmpty()) item { Text("该收藏夹暂无视频") }
        }
    }
}

@Composable
private fun FolderEditorDialog(initial: CoreFavFolder?, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, String, Int) -> Unit) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var intro by rememberSaveable(initial?.id) { mutableStateOf("") }
    var private by rememberSaveable(initial?.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "新建收藏夹" else "编辑收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = intro, onValueChange = { intro = it }, label = { Text("简介（可选）") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = private, onCheckedChange = { private = it })
                    Text(if (private) "私密收藏夹" else "公开收藏夹")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title.trim(), intro.trim(), if (private) 1 else 0) }, enabled = !busy && title.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
