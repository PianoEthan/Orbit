package com.qx.orbit.bili.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.ui.components.rememberAdaptiveSurfaceTransformation
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import kotlin.math.roundToInt

@Composable
internal fun InteractionChoiceAnchors(
    state: InteractionPlaybackCoordinator.State,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state.session.question ?: return
    val question = state.node?.edges?.questions?.getOrNull(active.index) ?: return
    val dimension = state.node.edges.dimension ?: return
    if (question.type != 2 || state.session.transition != null || state.error != null) return
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        active.visibleChoiceIndices.forEach { index ->
            val choice = question.choices[index]
            val anchor = interactionAnchor(choice.x, choice.y, dimension.width, dimension.height, width, height)
                ?: return@forEach
            Button(
                onClick = { onChoose(index) },
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier
                    .offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) }
                    .offset(x = (-44).dp, y = (-24).dp)
                    .widthIn(min = 48.dp, max = 88.dp)
                    .heightIn(min = 48.dp)
            ) { Text(choice.option.ifBlank { "选项 ${index + 1}" }) }
        }
    }
}

@Composable
internal fun InteractionChoiceOverlay(
    coordinator: InteractionPlaybackCoordinator,
    showList: Boolean,
    onShowList: (Boolean) -> Unit,
    onChoose: (Int) -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by coordinator.state.collectAsState()
    if (!state.blocksControls) return
    val active = state.session.question
    val node = state.node
    val question = active?.let { node?.edges?.questions?.getOrNull(it.index) }
    val dimension = node?.edges?.dimension
    val anchored = question?.type == 2 && dimension != null && dimension.width > 0 && dimension.height > 0 &&
        active.visibleChoiceIndices.all { index ->
            val choice = question.choices[index]
            interactionAnchor(choice.x, choice.y, dimension.width, dimension.height, 1f, 1f) != null
        }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (state.probing || (state.session.transition != null && state.error == null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = if (state.probing) "检查剧情中..." else "正在切换剧情...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (state.error != null || state.ended) {
            val errorListState = rememberTransformingLazyColumnState()
            val errorSpec = rememberTransformationSpec()
            ScreenScaffold(
                scrollState = errorListState,
                edgeButton = {
                    EdgeButton(
                        onClick = onRestart,
                        buttonSize = EdgeButtonSize.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("重新开始")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
            ) { contentPadding ->
                TransformingLazyColumn(
                    state = errorListState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                    rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(errorListState)
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveTransformedHeight(this, errorSpec),
                            transformation = rememberAdaptiveSurfaceTransformation(errorSpec)
                        ) {
                            Text(
                                text = state.error ?: "剧情已结束",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (state.error != null && !state.requiresRestart && (state.session.transition != null || state.session.problem == null)) {
                        item {
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .adaptiveTransformedHeight(this, errorSpec),
                                transformation = rememberAdaptiveSurfaceTransformation(errorSpec)
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        } else if (active != null && question != null) {
            if (anchored && !showList) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    EdgeButton(
                        onClick = { onShowList(true) },
                        buttonSize = EdgeButtonSize.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("选项列表")
                    }
                }
            } else {
                val listState = rememberTransformingLazyColumnState()
                val transformationSpec = rememberTransformationSpec()
                val listFocus = remember { FocusRequester() }

                LaunchedEffect(active.index, state.session.visitId) {
                    listState.scrollToItem(0)
                    runCatching { listFocus.requestFocus() }
                }

                ScreenScaffold(
                    scrollState = listState,
                    edgeButton = {
                        if (anchored) {
                            EdgeButton(
                                onClick = { onShowList(false) },
                                buttonSize = EdgeButtonSize.Small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("坐标选项")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                ) { contentPadding ->
                    TransformingLazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = contentPadding.calculateTopPadding(),
                            bottom = contentPadding.calculateBottomPadding()
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(listFocus)
                            .focusable(),
                        rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
                    ) {
                        item {
                            ListHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .adaptiveTransformedHeight(this, transformationSpec),
                                transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = question.title.ifBlank { "选择剧情" },
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (active.remainingMs != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "倒计时 ${(active.remainingMs + 999) / 1000} 秒",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        items(
                            count = active.visibleChoiceIndices.size,
                            key = { active.visibleChoiceIndices[it] }
                        ) { itemIdx ->
                            val index = active.visibleChoiceIndices[itemIdx]
                            Button(
                                onClick = { onChoose(index) },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .adaptiveTransformedHeight(this, transformationSpec),
                                transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                            ) {
                                Text(
                                    text = question.choices[index].option.ifBlank { "选项 ${index + 1}" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
