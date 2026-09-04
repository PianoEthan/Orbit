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
import com.qx.orbit.bili.data.api.BilibiliIDConverter
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
    val uris = listOf(message.targetUri, message.targetNativeUri).filter(String::isNotBlank)
    val bvid = uris.firstNotNullOfOrNull { uri -> BV_ID_REGEX.find(uri)?.value }
    if (!bvid.isNullOrBlank()) return videoTargetRoute(bvid, 0L, message, uris)

    val uriAid = uris.firstNotNullOfOrNull { uri ->
        AV_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
    if (uriAid != null) {
        return videoTargetRoute(BilibiliIDConverter.aidToBv(uriAid), uriAid, message, uris)
    }

    uris.firstNotNullOfOrNull { uri -> OPUS_ID_REGEX.find(uri)?.groupValues?.getOrNull(1) }?.let { opusId ->
        return "opus_detail/$opusId"
    }
    uris.firstNotNullOfOrNull { uri -> DYNAMIC_ID_REGEX.find(uri)?.groupValues?.getOrNull(1) }?.let { dynamicId ->
        return "dynamic_detail/$dynamicId"
    }
    uris.firstNotNullOfOrNull { uri -> ARTICLE_ID_REGEX.find(uri)?.groupValues?.getOrNull(1) }?.let { articleId ->
        return "article_detail/$articleId"
    }

    val subjectId = message.subjectId.takeIf { it > 0L } ?: return null
    return when {
        message.businessId == VIDEO_BUSINESS_ID -> videoTargetRoute(
            BilibiliIDConverter.aidToBv(subjectId),
            subjectId,
            message,
            uris
        )
        message.itemType.lowercase() in DYNAMIC_ITEM_TYPES ||
            message.businessId in DYNAMIC_BUSINESS_IDS -> "opus_detail/$subjectId"
        message.itemType.equals("article", ignoreCase = true) ||
            message.businessId == ARTICLE_BUSINESS_ID -> "article_detail/$subjectId"
        else -> null
    }
}

private fun videoTargetRoute(
    bvid: String,
    aid: Long,
    message: MessageCard,
    uris: List<String>
): String {
    val queryRootId = uris.firstNotNullOfOrNull { uri ->
        COMMENT_ROOT_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
    val fragmentReplyId = uris.firstNotNullOfOrNull { uri ->
        REPLY_FRAGMENT_REGEX.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
    val isCommentMessage = queryRootId != null ||
        fragmentReplyId != null ||
        message.rootId > 0L ||
        message.itemType.equals("reply", ignoreCase = true) ||
        message.itemType.equals("comment", ignoreCase = true) ||
        message.getType == MessageCard.GET_TYPE_REPLY
    if (!isCommentMessage) return "detail/$bvid/$aid"

    val rootId = queryRootId
        ?: message.rootId.takeIf { it > 0L }
        ?: message.sourceId.takeIf { it > 0L }
        ?: fragmentReplyId
        ?: message.targetId.takeIf { it > 0L }
        ?: return "detail/$bvid/$aid"
    val secondaryId = uris.firstNotNullOfOrNull { uri ->
        COMMENT_SECONDARY_ID_REGEX.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()
    } ?: message.sourceId.takeIf { it > 0L && it != rootId }
        ?: fragmentReplyId.takeIf { it != null && it != rootId }
        ?: 0L
    return "detail/$bvid/$aid?commentRootId=$rootId&commentReplyId=$secondaryId"
}

private val BV_ID_REGEX = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
private val AV_ID_REGEX = Regex("(?:bilibili\\.com/video/av|bilibili://video/)(\\d+)", RegexOption.IGNORE_CASE)
private val OPUS_ID_REGEX = Regex("(?:bilibili\\.com/opus/|bilibili://opus/detail/)(\\d+)", RegexOption.IGNORE_CASE)
private val DYNAMIC_ID_REGEX = Regex("(?:t\\.bilibili\\.com/|bilibili://dynamic/)(\\d+)", RegexOption.IGNORE_CASE)
private val ARTICLE_ID_REGEX = Regex("(?:bilibili\\.com/read/cv|bilibili://article/)(\\d+)", RegexOption.IGNORE_CASE)
private val COMMENT_ROOT_ID_REGEX = Regex("[?&]comment_root_id=(\\d+)", RegexOption.IGNORE_CASE)
private val COMMENT_SECONDARY_ID_REGEX = Regex("[?&]comment_secondary_id=(\\d+)", RegexOption.IGNORE_CASE)
private val REPLY_FRAGMENT_REGEX = Regex("#reply(\\d+)", RegexOption.IGNORE_CASE)
private val DYNAMIC_ITEM_TYPES = setOf("dynamic", "album", "opus")
private val DYNAMIC_BUSINESS_IDS = setOf(11, 17)
private const val VIDEO_BUSINESS_ID = 1
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
