package com.qx.orbit.bili.vision

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CoreDanmaku

@Composable
internal fun VisionDanmakuComposerOverlay(
    progressMs: Long,
    cid: Long,
    bvid: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VisionDanmakuSendViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var nineKey by rememberSaveable { mutableStateOf(isTv || VisionPreferences.preferNineKey) }
    var draft by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(CoreDanmaku.COLORS.first().value) }
    var mode by rememberSaveable { mutableIntStateOf(CoreDanmaku.MODE_SCROLL) }
    val inputFocus = remember { FocusRequester() }
    VisionInitialFocus(inputFocus, cid)
    BackHandler(onBack = onDismiss)
    val length = draft.codePointCount(0, draft.length)
    val canSend = VisionUserSession.isLoggedIn && draft.isNotBlank() && length <= CoreDanmaku.MAX_LENGTH && !state.sending && !state.sent
    val doSend: () -> Unit = { if (canSend) viewModel.send(cid, bvid, draft, progressMs, color, mode, VisionUserSession.cookie, VisionUserSession.csrf) }

    Surface(modifier.widthIn(max = 760.dp).padding(16.dp).visionFocusBorder(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("发送弹幕", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = nineKey, onClick = { nineKey = !nineKey }, label = { Text(if (nineKey) "九键" else "输入法") })
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("弹幕内容") },
                singleLine = true,
                readOnly = nineKey,
                enabled = !state.sending && !state.sent,
                isError = length > CoreDanmaku.MAX_LENGTH,
                supportingText = { Text("$length / ${CoreDanmaku.MAX_LENGTH} 字") },
                modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreDanmaku.COLORS.forEach { preset ->
                    FilterChip(selected = color == preset.value, onClick = { color = preset.value }, label = { Text(preset.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreDanmaku.MODES.forEach { preset ->
                    FilterChip(selected = mode == preset.value, onClick = { mode = preset.value }, label = { Text(preset.label) })
                }
            }
            if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (state.sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.sent) {
                    Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) { Text("关闭") }
                } else {
                    Button(onClick = doSend, enabled = canSend, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) {
                        Text(if (state.sending) "正在发送…" else "发送")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
                }
            }
            if (nineKey && !state.sent) {
                VisionNineKeyKeyboard(
                    onCharacter = { draft += it },
                    onDelete = { if (draft.isNotEmpty()) draft = deleteSearchCharacter(draft) },
                    onSearch = doSend,
                    canSearch = canSend,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
