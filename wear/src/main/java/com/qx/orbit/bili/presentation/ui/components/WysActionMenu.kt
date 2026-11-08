package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior

data class WysActionMenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun WysActionMenu(
    show: Boolean,
    title: String,
    items: List<WysActionMenuItem>,
    onDismissRequest: () -> Unit
) {
    Dialog(
        visible = show,
        onDismissRequest = onDismissRequest
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        ScreenScaffold(
            timeText = { WysTimeText() },
            scrollState = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                items(items, key = { it.label }) { action ->
                    Button(
                        onClick = {
                            onDismissRequest()
                            action.onClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                        icon = {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null
                            )
                        },
                        colors = if (action.destructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                iconColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                iconColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    ) {
                        Text(action.label)
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
