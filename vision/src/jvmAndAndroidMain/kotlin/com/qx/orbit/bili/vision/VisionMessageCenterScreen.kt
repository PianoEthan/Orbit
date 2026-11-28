package com.qx.orbit.bili.vision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qx.orbit.bili.core.api.CoreChatSession
import com.qx.orbit.bili.core.api.CoreNotice
import com.qx.orbit.bili.core.api.CoreUnreadCount
import java.text.DateFormat
import java.util.Date

@Composable
internal fun VisionMessageCenterScreen(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    viewModel: VisionMessageCenterViewModel = viewModel { VisionMessageCenterViewModel() },
) {
    val state by viewModel.state.collectAsState()
    val backFocus = remember { FocusRequester() }
    VisionInitialFocus(backFocus)
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("消息", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                if (state.unread.total > 0) {
                    Text(
                        "未读 ${if (state.unread.total > 99) "99+" else state.unread.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 600.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(200.dp).fillMaxHeight().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            VisionMessageTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = tab == state.tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    label = { Text(tab.title + unreadSuffix(state.unread, tab), modifier = Modifier.fillMaxWidth()) },
                                )
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MessageTabContent(state, Modifier.weight(1f).fillMaxHeight(), onOpenChat, viewModel)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VisionMessageTab.entries.forEach { tab ->
                                FilterChip(
                                    selected = tab == state.tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    label = { Text(tab.title + unreadSuffix(state.unread, tab)) },
                                )
                            }
                        }
                        MessageTabContent(state, Modifier.weight(1f).fillMaxWidth(), onOpenChat, viewModel)
                    }
                }
            }
        }
    }
}

private fun unreadSuffix(unread: CoreUnreadCount, tab: VisionMessageTab): String {
    val count = when (tab) {
        VisionMessageTab.CHAT -> unread.privateMessage
        VisionMessageTab.REPLY -> unread.reply
        VisionMessageTab.LIKE -> unread.like
        VisionMessageTab.AT -> unread.at
        VisionMessageTab.SYSTEM -> unread.system
    }
    return if (count > 0) "(${if (count > 99) "99+" else count})" else ""
}

@Composable
private fun MessageTabContent(
    state: VisionMessageCenterState,
    modifier: Modifier,
    onOpenChat: (Long) -> Unit,
    viewModel: VisionMessageCenterViewModel,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val empty = if (state.tab == VisionMessageTab.CHAT) state.sessions.isEmpty() else state.notices.isEmpty()
        if (empty) {
            when {
                state.loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::retry) { Text("重试") }
                }
                else -> Text("暂无${state.tab.title}消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else BoxWithConstraints(modifier) {
            val sessionRow: @Composable (CoreChatSession) -> Unit = { session ->
                val user = state.sessionUsers[session.talkerId]
                MessageRow(
                    imageUrl = user?.face.orEmpty(),
                    title = user?.name ?: "用户 ${session.talkerId}",
                    subtitle = session.lastMessage?.content?.ifBlank { "[消息]" }.orEmpty(),
                    trailing = session.lastMessage?.timestamp?.let { formatVisionMessageTime(it) }.orEmpty(),
                    unread = session.unread,
                    onClick = { onOpenChat(session.talkerId) },
                )
            }
            val noticeRow: @Composable (CoreNotice) -> Unit = { notice ->
                MessageRow(
                    imageUrl = notice.users.firstOrNull()?.face.orEmpty(),
                    title = notice.users.joinToString("、") { it.name }.ifBlank { "系统通知" },
                    subtitle = notice.content,
                    trailing = if (notice.timeStamp > 0) formatVisionMessageTime(notice.timeStamp) else "",
                    roundedImage = true,
                    onClick = {},
                )
            }
            val footer: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        state.hasMore -> FilledTonalButton(onClick = viewModel::loadMore) { Text("加载更多") }
                        state.error != null -> FilledTonalButton(onClick = viewModel::retry) { Text("重试加载更多") }
                        else -> Text("已加载全部消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val columns = visionListColumns(maxWidth.value)
            if (columns == 1) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.tab == VisionMessageTab.CHAT) {
                        items(state.sessions, key = { it.talkerId }) { session -> sessionRow(session) }
                    } else {
                        items(state.notices, key = { it.id }) { notice -> noticeRow(notice) }
                        item { footer() }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.tab == VisionMessageTab.CHAT) {
                        items(state.sessions, key = { it.talkerId }) { session -> sessionRow(session) }
                    } else {
                        items(state.notices, key = { it.id }) { notice -> noticeRow(notice) }
                        item(span = { GridItemSpan(maxLineSpan) }) { footer() }
                    }
                }
            }
        }
    }
}

internal fun formatVisionMessageTime(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))

@Composable
private fun MessageRow(
    imageUrl: String,
    title: String,
    subtitle: String,
    trailing: String,
    unread: Int = 0,
    roundedImage: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val context = LocalPlatformContext.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = remember(imageUrl, roundedImage, context) {
                    if (imageUrl.isBlank()) null else ImageRequest.Builder(context)
                        .data(if (roundedImage) imageUrl else visionThumbnailUrl(imageUrl, 160))
                        .size(160)
                        .bilibiliReferer().crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(if (roundedImage) CircleShape else MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trailing.isNotBlank()) {
                    Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
            }
        }
    }
}
