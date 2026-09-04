package com.qx.orbit.bili.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.model.FavoriteFolder
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.ui.components.rememberAdaptiveSurfaceTransformation
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior

@Composable
internal fun FavoriteFolderEditor(
    folder: FavoriteFolder,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var title by rememberSaveable(folder.mediaId) { mutableStateOf(folder.name) }
    var intro by rememberSaveable(folder.mediaId) { mutableStateOf(folder.intro) }
    var isPrivate by rememberSaveable(folder.mediaId) { mutableStateOf(folder.isPrivate) }

    Dialog(visible = true, onDismissRequest = onDismiss) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
        ScreenScaffold(timeText = { WysTimeText() }, scrollState = listState) { padding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = padding,
                modifier = Modifier.fillMaxSize().imePadding(),
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item {
                    ListHeader(modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)) {
                        Text(if (folder.mediaId == 0L) "新建收藏夹" else "编辑收藏夹")
                    }
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        enabled = !isSaving,
                        singleLine = true,
                        label = { Text("名称") },
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            .adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
                item {
                    OutlinedTextField(
                        value = intro,
                        onValueChange = { intro = it },
                        enabled = !isSaving,
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("简介") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            .adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
                item {
                    SwitchButton(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !isSaving,
                        label = { Text("私密收藏夹") },
                        secondaryLabel = { Text(if (isPrivate) "仅自己可见" else "所有人可见") },
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    )
                }
                if (error != null) {
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp)
                                .adaptiveTransformedHeight(this, transformationSpec)
                        )
                    }
                }
                item {
                    Button(
                        onClick = { onSave(title, intro, isPrivate) },
                        enabled = !isSaving && title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Text(if (isSaving) "保存中…" else "保存")
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
