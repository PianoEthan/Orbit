package com.qx.orbit.bili.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.model.PrivateMessage
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.ui.components.PrivateMessageBubble
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.ui.components.rememberAdaptiveSurfaceTransformation
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.PrivateChatViewModel
import kotlinx.coroutines.delay

@Composable
fun PrivateChatScreen(
    talkerId: Long,
    viewModel: PrivateChatViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val selfUid = CookieManager.getMid()
    val bubbleMaxWidth = (configuration.screenWidthDp.dp * 0.78f).coerceIn(132.dp, 196.dp)
    var showInput by remember { mutableStateOf(false) }

    LaunchedEffect(talkerId) {
        viewModel.open(talkerId)
    }
    LaunchedEffect(state.messages.lastOrNull()?.stableKey()) {
        val newest = state.messages.lastOrNull()
        val atBottom = listState.layoutInfo.visibleItems.firstOrNull()?.index in listOf(null, 0)
        if (newest != null && (newest.uid == selfUid || atBottom)) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(state.sendErrorMessage) {
        state.sendErrorMessage?.let { message ->
            RoundToast.show(context, message)
            viewModel.clearSendError()
        }
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        edgeButton = {
            val edgeButtonSize = EdgeButtonSize.ExtraSmall
            EdgeButton(
                onClick = { showInput = true },
                modifier = Modifier.fillMaxWidth(),
                buttonSize = edgeButtonSize,
                enabled = !state.isLoading && state.errorMessage == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "输入私信",
                    modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(edgeButtonSize))
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        when {
            state.isLoading && state.messages.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            state.errorMessage != null && state.messages.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = viewModel::retry) { Text("重试") }
                }
            }
            else -> {
                val displayMessages = state.messages.asReversed()
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = contentPadding,
                    rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
                ) {
                    item { Spacer(modifier = Modifier.height(56.dp)) }
                    if (displayMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无消息\n发条私信打个招呼吧",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = displayMessages,
                        key = { index, message -> message.stableKey(index) }
                    ) { index, message ->
                        PrivateMessageBubble(
                            message = message,
                            isMine = message.uid == selfUid,
                            maxWidth = bubbleMaxWidth,
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .animateItem()
                        )
                        if (index >= displayMessages.lastIndex - 2 &&
                            state.hasMore &&
                            !state.isLoadingOlder
                        ) {
                            LaunchedEffect(index) {
                                viewModel.loadOlder()
                            }
                        }
                    }
                    if (state.isLoadingOlder) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding())) }
                }
            }
        }
    }

    PrivateMessageInputDialog(
        visible = showInput,
        userName = state.user?.name?.ifBlank { null } ?: "用户 $talkerId",
        isSending = state.isSending,
        onDismiss = { showInput = false },
        onSend = { text ->
            viewModel.send(text) {
                showInput = false
            }
        }
    )
}

@Composable
private fun PrivateMessageInputDialog(
    visible: Boolean,
    userName: String,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    if (!visible) return
    var text by remember(visible) { mutableStateOf("") }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val focusRequester = remember { FocusRequester() }
    val submit = {
        val value = text.trim()
        if (value.isNotEmpty() && !isSending) onSend(value)
    }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            delay(250L)
            focusRequester.requestFocus()
        }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    val edgeButtonSize = EdgeButtonSize.ExtraSmall
                    EdgeButton(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth(),
                        buttonSize = edgeButtonSize,
                        enabled = text.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            iconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送私信",
                                modifier = Modifier.size(EdgeButtonDefaults.iconSizeFor(edgeButtonSize))
                            )
                        }
                    }
                }
            ) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                    rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                            transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                        ) {
                            Text(
                                text = "私信 $userName",
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = {
                                Text("输入消息", color = MaterialTheme.colorScheme.outline)
                            },
                            enabled = !isSending,
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .adaptiveTransformedHeight(this, transformationSpec)
                        )
                    }
                    item { Spacer(modifier = Modifier.height(36.dp)) }
                }
            }
        }
    }
}

private fun PrivateMessage.stableKey(index: Int = 0): String = when {
    msgSeqno > 0L -> "server_$msgSeqno"
    localId > 0L -> "local_$localId"
    msgId > 0L -> "id_$msgId"
    else -> "fallback_${uid}_${timestamp}_${type}_$index"
}
