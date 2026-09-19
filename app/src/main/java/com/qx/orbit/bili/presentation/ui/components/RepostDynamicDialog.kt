package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.RepostDynamicViewModel

@Composable
fun RepostDynamicDialog(
    dynamic: Dynamic,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: RepostDynamicViewModel = viewModel(key = "repost_${dynamic.dynamicId}")
) {
    val text by viewModel.text.collectAsState()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.sent) {
        if (state.sent) {
            onSuccess()
            viewModel.reset()
            RoundToast.show(context, "转发成功")
        }
    }

    Dialog(
        visible = true,
        onDismissRequest = {
            if (!state.isSending) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val isRound = LocalScreenRound.current
        val focusManager = LocalFocusManager.current

        ScreenScaffold(scrollState = listState) {
            TransformingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item { ListHeader { Text("转发动态", color = MaterialTheme.colorScheme.primary) } }
                item {
                    Text(
                        text = "@${dynamic.userInfo?.name.orEmpty()}：" +
                            dynamic.content.ifBlank { dynamic.archiveTitle.ifBlank { dynamic.title } },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    OutlinedTextField(
                        value = text,
                        onValueChange = viewModel::edit,
                        enabled = !state.isSending,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("说点什么（可选）") },
                        maxLines = 5,
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
                state.error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.send(dynamic.dynamicId)
                        },
                        enabled = !state.isSending && !state.sent && dynamic.canForward,
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("转发")
                        }
                    }
                }
            }
        }
    }
}
