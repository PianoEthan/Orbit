package com.qx.orbit.bili.vision

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CoreChatMessage

@Composable
internal fun VisionPrivateChatScreen(
    talkerId: Long,
    sessionType: Int,
    onBack: () -> Unit,
    viewModel: VisionPrivateChatViewModel = viewModel(),
) {
    val context = LocalConfiguration.current
    val isTv = context.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var nineKey by rememberSaveable { mutableStateOf(isTv || VisionPreferences.preferNineKey) }
    var draft by rememberSaveable { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)
    LaunchedEffect(talkerId, sessionType) { viewModel.open(talkerId, sessionType) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("私信 · 用户 $talkerId", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = nineKey,
                    onClick = { nineKey = !nineKey; keyboard?.hide() },
                    label = { Text(if (nineKey) "九键输入" else "系统输入法") },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.hasOlder) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                else -> FilledTonalButton(onClick = viewModel::loadOlder) { Text("加载更早消息") }
                            }
                        }
                    }
                }
                itemsIndexed(state.messages) { index, message ->
                    ChatBubble(message, isSelf = message.senderUid == VisionUserSession.mid)
                }
                if (state.error != null) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = viewModel::loadOlder) { Text("重试") }
                        }
                    }
                }
            }
            state.sendError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (nineKey) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("输入消息内容") },
                    minLines = 2,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.send(draft); draft = "" }, enabled = draft.isNotBlank() && !state.sending) {
                            if (state.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, "发送")
                        }
                    },
                )
                VisionNineKeyKeyboard(
                    onCharacter = { draft += it },
                    onDelete = { if (draft.isNotEmpty()) draft = deleteSearchCharacter(draft) },
                    onSearch = { viewModel.send(draft); draft = "" },
                    canSearch = draft.isNotBlank() && !state.sending,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("输入消息内容") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { viewModel.send(draft); draft = "" }, enabled = draft.isNotBlank() && !state.sending) {
                        if (state.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: CoreChatMessage, isSelf: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (isSelf) 18.dp else 4.dp,
                bottomEnd = if (isSelf) 4.dp else 18.dp,
            ),
            color = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    message.content.ifBlank { "[空消息]" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelf) FontWeight.Medium else FontWeight.Normal,
                )
                Text(
                    formatVisionMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
