package com.qx.orbit.bili.vision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun VisionReplyComposerScreen(
    aid: Long,
    root: Long,
    parent: Long,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSent: () -> Unit,
    viewModel: VisionReplyComposerViewModel = viewModel { VisionReplyComposerViewModel(createSavedStateHandle()) },
) {
    val draft by viewModel.draft.collectAsState()
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val isTv = visionIsTv()
    var nineKey by rememberSaveable { mutableStateOf(isTv || VisionPreferences.preferNineKey) }
    val editorFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val primaryModifier = Modifier.focusRequester(actionFocus).focusProperties { up = editorFocus }.heightIn(min = 56.dp)
    LaunchedEffect(cookie) { viewModel.setAccount(cookie) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回，保留草稿")
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = nineKey,
                onClick = { nineKey = !nineKey },
                label = { Text(if (nineKey) "九键输入 (遥控器)" else "系统输入法") },
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth >= 840.dp
            val editor: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (root == 0L) "写评论" else "回复评论", style = MaterialTheme.typography.headlineMedium)
                    Text(if (root == 0L) "分享你的看法" else if (parent == root) "回复原评论" else "回复楼中楼评论",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = draft, onValueChange = viewModel::edit,
                        modifier = Modifier.fillMaxWidth().focusRequester(editorFocus).onPreviewKeyEvent { event ->
                            if (!imeVisible && event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionDown, Key.DirectionUp)) {
                                if (event.key == Key.DirectionDown && (!VisionUserSession.isLoggedIn || draft.isNotBlank() || state.uncertain)) actionFocus.requestFocus()
                                else backFocus.requestFocus()
                                true
                            } else false
                        }, label = { Text("评论内容") },
                        readOnly = nineKey,
                        enabled = !state.sending && state.sentId == null, minLines = 5, maxLines = 10,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        isError = draft.codePointCount(0, draft.length) > 1000,
                        supportingText = { Text("${draft.codePointCount(0, draft.length)} / 1000 字 · 不会自动发布") })
                }
            }
            val actions: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("发布前确认", style = MaterialTheme.typography.titleLarge)
                    Text("请友善交流。评论将以当前登录账号公开发布。返回评论区核实后，重新打开同一回复入口可继续草稿；离开所属详情页后不再保留。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.message?.let {
                        Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = if (state.sentId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    when {
                        state.sentId != null -> Button(onClick = onSent, modifier = primaryModifier) { Text("返回评论区并刷新") }
                        !VisionUserSession.isLoggedIn -> FilledTonalButton(onClick = onLogin, modifier = primaryModifier) { Text("登录后发布") }
                        state.uncertain -> {
                            FilledTonalButton(onClick = onBack, modifier = primaryModifier) { Text("返回评论区核实") }
                            TextButton(onClick = viewModel::acknowledgeUncertain, modifier = Modifier.heightIn(min = 48.dp)) { Text("我已核实未发布，允许再次发送") }
                        }
                        else -> Button(onClick = viewModel::requestConfirmation, enabled = !state.sending && draft.isNotBlank(),
                            modifier = primaryModifier) { Text(if (state.sending) "正在发送，请勿重复操作" else "预览并发布") }
                    }
                }
            }
            val keyboard: @Composable () -> Unit = {
                VisionNineKeyKeyboard(
                    onCharacter = { viewModel.edit(draft + it) },
                    onDelete = { viewModel.edit(deleteSearchCharacter(draft)) },
                    onSearch = { if (draft.isNotBlank()) viewModel.requestConfirmation() },
                    canSearch = draft.isNotBlank() && !state.sending && state.sentId == null && VisionUserSession.isLoggedIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (wide) {
                Row(Modifier.widthIn(max = 1200.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Box(Modifier.weight(1.5f)) { editor() }
                    Box(Modifier.weight(1f).padding(top = 8.dp)) { actions() }
                    if (nineKey) Box(Modifier.widthIn(min = 320.dp, max = 380.dp)) { keyboard() }
                }
            } else {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    editor()
                    if (nineKey) keyboard()
                    actions()
                }
            }
        }
    }
    if (state.confirming) AlertDialog(
        onDismissRequest = viewModel::dismissConfirmation,
        title = { Text("公开发布这条评论？") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(draft)
            Text("确认后才会发送，不会自动重试。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { viewModel.send(aid, root, parent, VisionUserSession.csrf) }) { Text("确认发布") } },
        dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text("继续编辑") } },
    )
}
