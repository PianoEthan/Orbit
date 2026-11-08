package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.presentation.util.RotaryUtils
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun WysAlertDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable (() -> Unit)? = null,
    onConfirm: () -> Unit
) {
    if (!RotaryUtils.isWearHapticsAvailable) {
            Dialog(
                visible = show,
                onDismissRequest = onDismissRequest,
            ) {
                val listState = rememberTransformingLazyColumnState()
                val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

                ScreenScaffold(
                    scrollState = listState,
                    timeText = { WysTimeText() },
                ) {
                    TransformingLazyColumn(
                        state = listState,
                        contentPadding = it,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                                    .adaptiveTransformedHeight(this, transformationSpec),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                //Spacer(modifier = Modifier.height(24.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .graphicsLayer {
                                            with(transformationSpec) {
                                                applyContentTransformation(scrollProgress)
                                            }
                                        },
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = title,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            with(transformationSpec) {
                                                applyContentTransformation(scrollProgress)
                                            }
                                        }
                                )
                            }
                        }

                        if (content != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                                        .adaptiveTransformedHeight(this, transformationSpec),
                                    contentAlignment = Alignment.Center
                                ) {
                                    content()
                                }
                            }
                        }

                        item {
                            val dismissInteractionSource = remember { MutableInteractionSource() }
                            val confirmInteractionSource = remember { MutableInteractionSource() }
                            ButtonGroup(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp,
                                        vertical = 20.dp
                                    )
                                    .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                                    .adaptiveTransformedHeight(this, transformationSpec),
                                spacing = 8.dp
                            ) {
                                FilledIconButton(
                                    onClick =  { onDismissRequest() },
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    interactionSource = dismissInteractionSource,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.animateWidth(dismissInteractionSource)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                with(transformationSpec) {
                                                    applyContentTransformation(scrollProgress)
                                                }
                                            }
                                    )
                                }
                                FilledIconButton(
                                    onClick =  { onConfirm() },
                                    shapes = IconButtonDefaults.animatedShapes(),
                                    interactionSource = confirmInteractionSource,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.animateWidth(confirmInteractionSource)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                with(transformationSpec) {
                                                    applyContentTransformation(scrollProgress)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
    } else {
        AlertDialog(
            visible = show,
            onDismissRequest = onDismissRequest,
            modifier = Modifier
                .fillMaxSize(),
            icon = {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = content,
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = { onConfirm() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "确认",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            dismissButton = {
                AlertDialogDefaults.DismissButton(
                    onClick = { onDismissRequest() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
    }
}
