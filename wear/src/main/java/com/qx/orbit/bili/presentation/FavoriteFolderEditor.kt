package com.qx.orbit.bili.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val hideKeyboard = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.background,
            unfocusedBorderColor = MaterialTheme.colorScheme.background
        )
        ScreenScaffold(
            timeText = { WysTimeText() },
            scrollState = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) { padding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().imePadding(),
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            if (folder.mediaId == 0L) "新建收藏夹" else "编辑收藏夹",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        enabled = !isSaving,
                        singleLine = true,
                        placeholder = { Text("收藏夹名称", color = MaterialTheme.colorScheme.outline) },
                        shape = RoundedCornerShape(24.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = intro,
                        onValueChange = { intro = it },
                        enabled = !isSaving,
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("收藏夹简介", color = MaterialTheme.colorScheme.outline) },
                        shape = RoundedCornerShape(24.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                                .adaptiveTransformedHeight(this, transformationSpec)
                        )
                    }
                }
                item {
                    Button(
                        onClick = {
                            hideKeyboard()
                            onSave(title, intro, isPrivate)
                        },
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
