package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qx.orbit.bili.core.api.CoreSubtitleTrack

@Composable
internal fun VisionSubtitlePicker(state: VisionSubtitleState, onSelect: (CoreSubtitleTrack?) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    VisionInitialFocus(firstFocus, state.tracks.size)
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    AlertDialog(onDismissRequest = onDismiss, modifier = if (wide) Modifier.widthIn(max = 560.dp) else Modifier, properties = DialogProperties(usePlatformDefaultWidth = !wide), title = { Text("CC 字幕") }, text = {
        Column(Modifier.heightIn(max = if (wide) 480.dp else 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(firstFocus).visionFocusBorder()) {
                Text(if (state.selected == null) "✓ 关闭字幕" else "关闭字幕")
            }
            state.tracks.forEach { track ->
                TextButton(onClick = { onSelect(track) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).visionFocusBorder()) {
                    Text("${if (state.selected == track) "✓ " else ""}${track.label}${if (track.generated) " · AI" else ""}")
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = onRetry) { Text("重试字幕") }
            }
            if (!state.loading && state.error == null && state.tracks.isEmpty()) Text("该视频暂无可用字幕")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
