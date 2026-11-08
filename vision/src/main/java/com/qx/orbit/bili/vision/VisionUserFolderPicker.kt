package com.qx.orbit.bili.vision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qx.orbit.bili.core.api.CoreFavFolder

@Composable
internal fun VisionUserFolderPicker(folders: List<CoreFavFolder>, selectedId: Long?, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    val index = folders.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val list = rememberLazyListState(index)
    val focus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(folders, inputMode.inputMode) {
        if (folders.isNotEmpty() && inputMode.inputMode == InputMode.Keyboard) {
            list.scrollToItem(index)
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择收藏夹") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id }) { folder ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val selected = folder.id == selectedId
                Surface(onClick = { onSelect(folder.id) }, interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().then(if (folder == folders[index]) Modifier.focusRequester(focus) else Modifier)
                        .semantics { this.selected = selected; role = Role.RadioButton },
                    shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
                    Column(Modifier.padding(16.dp).heightIn(min = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${if (selected) "当前 · " else ""}${folder.title}", modifier = Modifier.fillMaxWidth(), softWrap = true, style = MaterialTheme.typography.titleSmall)
                        Text("${folder.mediaCount} 个视频", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
