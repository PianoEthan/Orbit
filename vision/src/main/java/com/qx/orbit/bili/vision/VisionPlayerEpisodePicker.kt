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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
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

@Composable
internal fun VisionPlayerEpisodePicker(
    state: VisionPlayerEpisodesState,
    currentCid: Long,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val index = state.pages.indexOfFirst { it.cid == currentCid }.coerceAtLeast(0)
    val listState = rememberLazyListState(index)
    val selectedFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(state.pages, inputMode.inputMode) {
        if (state.pages.isNotEmpty() && inputMode.inputMode == InputMode.Keyboard) {
            listState.scrollToItem(index)
            withFrameNanos { }
            selectedFocus.requestFocus()
        }
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    AlertDialog(onDismissRequest = onDismiss, modifier = if (wide) Modifier.widthIn(max = 560.dp) else Modifier, properties = DialogProperties(usePlatformDefaultWidth = !wide), title = { Text("选择分 P") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("新选集从开头播放，保留当前倍速和播放/暂停状态。", style = MaterialTheme.typography.bodySmall)
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) { Text("重新加载选集") }
                }
                state.pages.isEmpty() -> Text("暂无分 P")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (wide) 480.dp else 360.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.pages, key = { it.cid }) { page ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    val selected = page.cid == currentCid
                    Surface(onClick = { onSelect(page.cid) }, interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth().then(if (page == state.pages[index]) Modifier.focusRequester(selectedFocus) else Modifier)
                            .semantics { this.selected = selected; role = Role.RadioButton },
                        shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(16.dp).heightIn(min = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${if (selected) "正在播放 · " else ""}P${page.page} ${page.part.ifBlank { "第 ${page.page} 集" }}", style = MaterialTheme.typography.titleSmall)
                            Text("${page.durationSeconds / 60}:${(page.durationSeconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
