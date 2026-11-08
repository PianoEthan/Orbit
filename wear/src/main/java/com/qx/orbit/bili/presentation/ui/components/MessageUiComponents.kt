package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.data.model.MessageCard
import com.qx.orbit.bili.data.model.PrivateMessage
import com.qx.orbit.bili.data.model.PrivateMsgSession
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.model.displayText
import com.qx.orbit.bili.data.model.imageUrl
import com.qx.orbit.bili.data.model.previewText
import com.qx.orbit.bili.util.fixCoverUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

@Composable
fun PrivateSessionCard(
    session: PrivateMsgSession,
    user: UserInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = if (session.unread > 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (session.unread > 0) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UserAvatar(
                avatarUrl = user?.avatar.orEmpty(),
                officialRole = user?.official ?: 0,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.name?.ifBlank { null } ?: "用户 ${session.talkerUid}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.previewText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.unread > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.unread > 0) {
                UnreadBadge(session.unread)
            }
        }
    }
}

@Composable
fun NotificationMessageCard(
    message: MessageCard,
    onClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (message.user.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.user.take(3).forEach { user ->
                        UserAvatar(
                            avatarUrl = user.avatar,
                            officialRole = user.official,
                            onClick = { onUserClick(user.mid) },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = message.user.joinToString("、") { it.name }.ifBlank { "哔哩哔哩用户" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (message.targetTitle.isNotBlank() || message.targetImage.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.targetImage.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.targetImage.fixCoverUrl())
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small)
                        )
                    }
                    Text(
                        text = message.targetTitle.ifBlank { "查看相关内容" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            val time = formatMessageTime(message.timeStamp, message.timeDesc)
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
fun PrivateMessageBubble(
    message: PrivateMessage,
    isMine: Boolean,
    maxWidth: Dp,
    modifier: Modifier = Modifier
) {
    if (message.type == PrivateMessage.TYPE_RETRACT || message.type == PrivateMessage.TYPE_SYSTEM) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                text = message.displayText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        return
    }

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 6.dp,
        bottomEnd = if (isMine) 6.dp else 18.dp
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .alpha(if (message.isPending) 0.68f else 1f)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val imageUrl = message.imageUrl()
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl.fixCoverUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = message.displayText(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            if (message.type != PrivateMessage.TYPE_PIC && message.type != PrivateMessage.TYPE_FACE) {
                Text(
                    text = message.displayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.64f)
                )
                if (message.isPending) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "发送中",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.64f)
                    )
                }
            }
        }
    }
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = if (count > 99) "99+" else count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onError,
        textAlign = TextAlign.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

fun formatMessageTime(timestamp: Long, fallback: String = ""): String {
    if (timestamp <= 0L) return fallback
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp * 1000L))
}
