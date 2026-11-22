package com.qx.orbit.bili.vision

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionDynamicDetailScreen(
    dynamicId: String,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onVideo: (CoreVideo) -> Unit,
    onImages: (List<String>, Int) -> Unit,
    viewModel: VisionDynamicDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var nineKey by rememberSaveable { mutableStateOf(isTv || VisionPreferences.preferNineKey) }
    var draft by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val backFocus = remember { FocusRequester() }
    VisionInitialFocus(backFocus, state.dynamic?.id)
    BackHandler(onBack = onBack)
    LaunchedEffect(dynamicId, cookie) { viewModel.open(dynamicId, cookie) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    val requestRepost: () -> Unit = { if (VisionUserSession.isLoggedIn && !state.reposting && !state.reposted) confirming = true }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.error != null -> {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = { viewModel.open(dynamicId, cookie) }) { Text("重试动态") }
                }
                state.dynamic != null -> {
                    DynamicBody(state.dynamic!!, onVideo, onImages)
                    HorizontalDivider()
                    Text("转发动态", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.reposted -> Text(state.repostMessage ?: "转发成功", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        !VisionUserSession.isLoggedIn -> FilledTonalButton(onClick = onLogin, modifier = Modifier.heightIn(min = 48.dp)) { Text("登录后转发") }
                        else -> {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                label = { Text("转发说说（可留空）") },
                                readOnly = nineKey,
                                enabled = !state.reposting,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (state.reposting) LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.repostMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            Button(onClick = requestRepost, enabled = !state.reposting, modifier = Modifier.heightIn(min = 48.dp).visionFocusBorder()) {
                                Text(if (state.reposting) "正在转发，请勿重复操作" else "转发")
                            }
                        }
                    }
                }
            }
        }
    }
    val nineKeyBoard: @Composable () -> Unit = {
        VisionNineKeyKeyboard(
            onCharacter = { draft += it },
            onDelete = { if (draft.isNotEmpty()) draft = deleteSearchCharacter(draft) },
            onSearch = requestRepost,
            canSearch = VisionUserSession.isLoggedIn && !state.reposting && !state.reposted,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("动态详情", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                FilterChip(selected = nineKey, onClick = { nineKey = !nineKey; keyboard?.hide() }, label = { Text(if (nineKey) "九键输入" else "系统输入法") })
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                            content(Modifier.widthIn(max = 840.dp).fillMaxHeight())
                        }
                        if (nineKey && VisionUserSession.isLoggedIn && !state.reposted) {
                            Column(Modifier.widthIn(min = 320.dp, max = 380.dp).fillMaxHeight()) { nineKeyBoard() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        content(Modifier.weight(1f).fillMaxWidth())
                        if (nineKey && VisionUserSession.isLoggedIn && !state.reposted) nineKeyBoard()
                    }
                }
            }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("公开转发这条动态？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(draft.ifBlank { "转发动态" })
                Text("将以当前登录账号公开发布到你的动态，确认后才会发送。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                confirming = false
                viewModel.repost(dynamicId, draft, cookie, VisionUserSession.csrf)
                draft = ""
            }) { Text("确认转发") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("继续编辑") } },
    )
}
