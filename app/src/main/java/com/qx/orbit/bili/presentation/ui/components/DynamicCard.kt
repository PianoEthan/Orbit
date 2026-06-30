package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.wear.compose.material3.SurfaceTransformation
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.presentation.util.parseRichText
import com.qx.orbit.bili.presentation.theme.BiliPink
import com.qx.orbit.bili.util.formatCount

@Composable
fun DynamicCard(
    item: Dynamic, 
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    onClick: () -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onArchiveClick: (String, Long) -> Unit = { _, _ -> },
    onLiveClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val getImageRequest = { url: String, isCover: Boolean ->
        val fixedUrl = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
        val finalUrl = if (!fixedUrl.contains("@")) {
            if (isCover) "$fixedUrl@480w_270h_1c.webp" else "$fixedUrl@400w.webp"
        } else fixedUrl
        ImageRequest.Builder(context)
            .data(finalUrl)
            .crossfade(true)
            .build()
    }

    Card(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth(),
        transformation = transformation
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                UserAvatar(
                    avatarUrl = item.userInfo?.avatar ?: "",
                    officialRole = item.userInfo?.official ?: 0,
                    modifier = Modifier.size(28.dp),
                    isVip = (item.userInfo?.vip_role ?: 0) > 0
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    UserNameText(
                        name = item.userInfo?.name ?: "Unknown",
                        isVip = (item.userInfo?.vip_role ?: 0) > 0,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (item.major_type) {
                            "MAJOR_TYPE_ARCHIVE" -> "投稿了视频"
                            "MAJOR_TYPE_LIVE_RCMD" -> "直播了"
                            else -> "发布了动态"
                        },
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            if (item.title.isNotEmpty()) {
                Text(text = item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (item.content.isNotEmpty() && item.content != item.archiveTitle) {
                val (richText, inlineContent) = parseRichText(item.content, item.emotes, item.members, emptyMap())
                val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    text = richText,
                    inlineContent = inlineContent,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val pos = textLayoutResult.value?.getOffsetForPosition(offset) ?: -1
                            var handled = false
                            if (pos >= 0) {
                                richText.getStringAnnotations("USER", pos, pos).firstOrNull()?.let {
                                    onUserClick(it.item.toLongOrNull() ?: 0L)
                                    handled = true
                                }
                            }
                            if (!handled) onClick()
                        }
                    },
                    onTextLayout = { textLayoutResult.value = it }
                )
            }
            
            if (item.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                if (item.images.size == 1) {
                    AsyncImage(
                        model = getImageRequest(item.images.first(), false),
                        contentDescription = null,
                        modifier = Modifier.height(100.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.images.take(3).forEach { imgUrl ->
                            AsyncImage(
                                model = getImageRequest(imgUrl, false),
                                contentDescription = null,
                                modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            
            if (item.major_type == "MAJOR_TYPE_ARCHIVE" && item.cover.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                RecommendVideoCard(
                    item = VideoCard(
                        title = item.archiveTitle,
                        cover = item.cover,
                        upName = item.userInfo?.name ?: "",
                        view = "视频"
                    ),
                    onClick = {
                        if (item.bvid.isNotEmpty() || item.comment_id > 0) {
                            onArchiveClick(item.bvid, item.comment_id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (item.major_type == "MAJOR_TYPE_LIVE_RCMD" && item.cover.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                RecommendVideoCard(
                    item = VideoCard(
                        title = item.archiveTitle,
                        cover = item.cover,
                        upName = item.userInfo?.name ?: "",
                        view = if (item.liveState == 1) "直播中" else if (item.liveState == 2) "轮播中" else "已结束"
                    ),
                    onClick = {
                        val roomId = item.bvid.toLongOrNull() ?: 0L
                        if (roomId > 0) {
                            onLiveClick(roomId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item.dynamic_forward?.let { forward ->
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(6.dp)
                ) {
                    Text(text = "@${forward.userInfo?.name ?: "已失效动态"}", fontSize = 10.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (forward.content.isNotEmpty() && forward.content != forward.archiveTitle) {
                        val (richText, inlineContent) = parseRichText(forward.content, forward.emotes, forward.members, emptyMap())
                        val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                        Text(
                            text = richText,
                            inlineContent = inlineContent,
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val pos = textLayoutResult.value?.getOffsetForPosition(offset) ?: -1
                                    var handled = false
                                    if (pos >= 0) {
                                        richText.getStringAnnotations("USER", pos, pos).firstOrNull()?.let {
                                            onUserClick(it.item.toLongOrNull() ?: 0L)
                                            handled = true
                                        }
                                    }
                                    if (!handled) onClick()
                                }
                            },
                            onTextLayout = { textLayoutResult.value = it }
                        )
                    }
                    if (forward.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (forward.images.size == 1) {
                            AsyncImage(
                                model = getImageRequest(forward.images.first(), false),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                forward.images.take(3).forEach { imgUrl ->
                                    AsyncImage(
                                        model = getImageRequest(imgUrl, false),
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    if (forward.major_type == "MAJOR_TYPE_ARCHIVE" && forward.cover.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        RecommendVideoCard(
                            item = VideoCard(
                                title = forward.archiveTitle,
                                cover = forward.cover,
                                upName = forward.userInfo?.name ?: "",
                                view = "视频"
                            ),
                            onClick = {
                                if (forward.bvid.isNotEmpty() || forward.comment_id > 0) {
                                    onArchiveClick(forward.bvid, forward.comment_id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Like
                val likeColor = if (item.stats?.liked == true) BiliPink else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.wear.compose.material3.Icon(
                        painter = painterResource(R.drawable.icon_like_0),
                        contentDescription = "Like",
                        modifier = Modifier.size(12.dp),
                        tint = likeColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(item.stats?.like ?: 0),
                        fontSize = 10.sp,
                        color = likeColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Share
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.wear.compose.material3.Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(item.stats?.share ?: 0), 
                        fontSize = 10.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Reply
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.wear.compose.material3.Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Reply",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(item.stats?.reply ?: 0), 
                        fontSize = 10.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.pubTime, 
                    fontSize = 10.sp, 
                    color = Color.Gray,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
