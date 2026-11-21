package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun VisionFavoritePicker(
    cookie: String,
    mid: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    viewModel: VisionFavoritePickerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val firstFocus = remember { FocusRequester() }
    VisionInitialFocus(firstFocus, state.folders.size)
    LaunchedEffect(cookie, mid) { viewModel.load(cookie, mid) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏到收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择一个收藏夹，仅添加到所选夹，不会从其他收藏夹移除。", style = MaterialTheme.typography.bodyMedium)
                when {
                    state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error != null -> {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        FilledTonalButton(onClick = { viewModel.load(cookie, mid) }) { Text("重试收藏夹") }
                    }
                    state.folders.isEmpty() -> Text("当前没有可用收藏夹")
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(state.folders, key = { _, folder -> folder.id }) { index, folder ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = state.selected == folder.id, role = Role.RadioButton, onClick = { viewModel.select(folder.id) })
                                    .heightIn(min = 56.dp).padding(12.dp)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                    .visionFocusBorder(),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(state.selected == folder.id, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(folder.title, style = MaterialTheme.typography.bodyLarge)
                                    Text("${folder.mediaCount} 个视频", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { state.selected?.let(onConfirm) }, enabled = !state.loading && state.error == null && state.selected != null) { Text("收藏到此夹") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
