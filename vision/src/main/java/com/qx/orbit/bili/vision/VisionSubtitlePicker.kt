package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qx.orbit.bili.core.api.CoreSubtitleTrack

@Composable
internal fun VisionSubtitlePicker(state: VisionSubtitleState, onSelect: (CoreSubtitleTrack?) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("CC 字幕") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.selected == null) "✓ 关闭字幕" else "关闭字幕")
            }
            state.tracks.forEach { track ->
                TextButton(onClick = { onSelect(track) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
