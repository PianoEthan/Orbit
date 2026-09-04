package com.qx.orbit.bili.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.data.model.MessageCard
import com.qx.orbit.bili.presentation.ui.components.NotificationMessageCard
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.ui.components.rememberAdaptiveSurfaceTransformation
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.NotificationType
import com.qx.orbit.bili.presentation.viewmodel.NotificationViewModel

@Composable
fun NotificationScreen(
    typeValue: String,
    navController: NavController,
    viewModel: NotificationViewModel = viewModel()
) {
    val type = NotificationType.fromRoute(typeValue)
    val state by viewModel.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(type) {
        viewModel.load(type)
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        modifier = Modifier.fillMaxSize()
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
                    Text(type.title, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (state.errorMessage != null && state.items.isEmpty()) {
                item {
                    NotificationStatus(
                        message = state.errorMessage.orEmpty(),
                        modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
                item {
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Text("重试")
                    }
                }
            } else if (!state.isLoading && state.items.isEmpty()) {
                item {
                    NotificationStatus(
                        message = "暂无${type.title}",
                        modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec)
                    )
                }
            }

            itemsIndexed(
                items = state.items,
                key = { index, message ->
                    "${message.id}_${message.timeStamp}_${message.getType}_$index"
                }
            ) { index, message ->
                NotificationMessageCard(
                    message = message,
                    onClick = {
                        notificationTargetRoute(message)?.let(navController::navigate)
                    },
                    onUserClick = { mid -> navController.navigate("user_space/$mid") },
                    transformation = rememberAdaptiveSurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .animateItem()
                )
                if (index >= state.items.lastIndex - 2 && !state.isLoading && !state.isEnd) {
                    LaunchedEffect(index) {
                        viewModel.loadMore()
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .adaptiveTransformedHeight(this, transformationSpec),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (state.errorMessage != null && state.items.isNotEmpty()) {
                item {
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = rememberAdaptiveSurfaceTransformation(transformationSpec)
                    ) {
                        Text("加载失败，点击重试")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

private fun notificationTargetRoute(message: MessageCard): String? {
    val uri = message.targetUri
    val bvid = BV_ID_REGEX.find(uri)?.value
    if (!bvid.isNullOrBlank()) return "detail/$bvid/0"

    OPUS_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.let { opusId ->
        return "opus_detail/$opusId"
    }
    DYNAMIC_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.let { dynamicId ->
        return "dynamic_detail/$dynamicId"
    }
    ARTICLE_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.let { articleId ->
        return "article_detail/$articleId"
    }

    val subjectId = message.subjectId.takeIf { it > 0L } ?: return null
    return when {
        message.itemType.lowercase() in DYNAMIC_ITEM_TYPES ||
            message.businessId in DYNAMIC_BUSINESS_IDS -> "opus_detail/$subjectId"
        message.itemType.equals("article", ignoreCase = true) ||
            message.businessId == ARTICLE_BUSINESS_ID -> "article_detail/$subjectId"
        else -> null
    }
}

private val BV_ID_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
private val OPUS_ID_REGEX = Regex("(?:bilibili\\.com/opus/|bilibili://opus/detail/)(\\d+)", RegexOption.IGNORE_CASE)
private val DYNAMIC_ID_REGEX = Regex("(?:t\\.bilibili\\.com/|bilibili://dynamic/)(\\d+)", RegexOption.IGNORE_CASE)
private val ARTICLE_ID_REGEX = Regex("(?:bilibili\\.com/read/cv|bilibili://article/)(\\d+)", RegexOption.IGNORE_CASE)
private val DYNAMIC_ITEM_TYPES = setOf("dynamic", "album", "opus")
private val DYNAMIC_BUSINESS_IDS = setOf(11, 17)
private const val ARTICLE_BUSINESS_ID = 12

@Composable
private fun NotificationStatus(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
