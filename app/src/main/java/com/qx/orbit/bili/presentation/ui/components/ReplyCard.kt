package com.qx.orbit.bili.presentation.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.api.BilibiliIDConverter
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.util.parseRichText
import com.qx.orbit.bili.presentation.theme.BiliPink
import com.qx.orbit.bili.util.LinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.wear.compose.material3.*
import androidx.compose.material.icons.filled.Delete
import com.qx.orbit.bili.util.SharedPreferencesUtil
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.api.UserInfoApi
import androidx.compose.ui.res.stringResource
import com.qx.orbit.bili.data.remote.CookieManager


@Composable
fun ReplyCard(
    reply: Reply,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    navController: NavHostController,
    showReplyPreview: Boolean = true,
    isDetail: Boolean = false,
    replyType: Int = ReplyApi.REPLY_TYPE_VIDEO,
    onRemove: (Reply) -> Unit = {},
    onClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    onReplyClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var linkClicked by remember { mutableStateOf(false) }
    var resolvedB23Links by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showImageDialog by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    showImageDialog?.let { (images, index) ->
        ImageViewerDialog(
            imageUrls = images,
            initialIndex = index,
            onDismiss = { showImageDialog = null }
        )
    }

    LaunchedEffect(reply.message) {
        val b23Pattern = Regex("https?://b23\\.tv/\\S+", RegexOption.IGNORE_CASE)
        val b23Links = b23Pattern.findAll(reply.message).map { it.value }.toList()
        if (b23Links.isNotEmpty()) {
            val resolved = mutableMapOf<String, String>()
            for (link in b23Links) {
                withContext(Dispatchers.IO) {
                    LinkResolver.resolveB23Link(link)?.let { bv ->
                        resolved[link] = bv
                    }
                }
            }
            resolvedB23Links = resolved
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentMid = remember { CookieManager.getInfoFromCookie("DedeUserID").toLongOrNull() ?: 0 }
    val isOwnComment = reply.sender?.mid != null && reply.sender.mid == currentMid

    val performAction = {
        coroutineScope.launch {
            if (isOwnComment) {
                try {
                    ReplyApi.deleteReply(reply.oid, reply.rpid, replyType)
                    withContext(Dispatchers.Main) {
                        RoundToast.show(context, "已删除评论")
                        onRemove(reply)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        RoundToast.show(context, "删除失败: ${e.message}")
                    }
                }
            } else {
                try {
                    reply.sender?.mid?.let { UserInfoApi.blockUser(it) }
                    withContext(Dispatchers.Main) {
                        RoundToast.show(context, "已拉黑该用户")
                        onRemove(reply)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        RoundToast.show(context, "拉黑失败: ${e.message}")
                    }
                }
            }
        }
    }

    Card(
        onClick = { if (!linkClicked) onClick() else linkClicked = false },
        onLongClick = {
            showDeleteConfirmDialog = true
        },
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    reply.sender?.mid?.let { mid ->
                        navController.navigate("user_space/$mid")
                    }
                }
            ) {
                UserAvatar(
                    avatarUrl = reply.sender?.avatar ?: "",
                    officialRole = reply.sender?.official ?: 0,
                    modifier = Modifier.size(24.dp),
                    isVip = (reply.sender?.vip_role ?: 0) > 0
                )
                Spacer(modifier = Modifier.width(6.dp))
                UserNameText(
                    name = reply.sender?.name ?: "",
                    isVip = (reply.sender?.vip_role ?: 0) > 0,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                LevelIcon(
                    level = reply.sender?.level ?: 0,
                    isSenior = (reply.sender?.is_senior_member ?: 0) == 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            var isExpanded by remember { mutableStateOf(false) }
            var hasOverflow by remember { mutableStateOf(false) }
            val (richText, inlineContent) = parseRichText(reply.message, reply.emotes, reply.members, resolvedB23Links)
            val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = richText,
                inlineContent = inlineContent,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val pos = textLayoutResult.value?.getOffsetForPosition(offset) ?: -1
                        if (pos >= 0) {
                            richText.getStringAnnotations(tag = "URL", start = pos, end = pos).firstOrNull()?.let { annotation ->
                                linkClicked = true
                                val url = annotation.item.removePrefix("url:")
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            richText.getStringAnnotations(tag = "VIDEO", start = pos, end = pos).firstOrNull()?.let { annotation ->
                                linkClicked = true
                                val videoId = annotation.item.removePrefix("video:")
                                val aid = if (videoId.startsWith("av", ignoreCase = true)) videoId.removePrefix("av").toLongOrNull() ?: 0L else 0L
                                val bvid = if (videoId.startsWith("bv", ignoreCase = true)) videoId else BilibiliIDConverter.aidToBv(aid)
                                if (aid > 0 || bvid.isNotEmpty()) {
                                    navController.navigate("detail/$bvid/$aid")
                                }
                            }
                            richText.getStringAnnotations(tag = "USER", start = pos, end = pos).firstOrNull()?.let { annotation ->
                                linkClicked = true
                                val mid = annotation.item.toLongOrNull() ?: 0L
                                if (mid > 0) {
                                    navController.navigate("user_space/$mid")
                                }
                            }
                        }
                    }
                },
                maxLines = if (isDetail || isExpanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    textLayoutResult.value = it
                    if (!isDetail && !isExpanded) {
                        hasOverflow = it.hasVisualOverflow
                    }
                }
            )
            if (reply.pictureList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val isSingle = reply.pictureList.size == 1
                    val fixedImages = reply.pictureList.map { url ->
                        when {
                            url.startsWith("//") -> "https:$url"
                            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
                            else -> url
                        }
                    }
                    fixedImages.forEachIndexed { index, fixedUrl ->
                        AsyncImage(
                            model = fixedUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .height(80.dp)
                                .then(if (isSingle) Modifier else Modifier.width(80.dp))
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showImageDialog = Pair(fixedImages, index) },
                            contentScale = if (isSingle) ContentScale.Fit else ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isDetail && (hasOverflow || isExpanded)) {
                    Text(
                        text = if (isExpanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(end = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = reply.pubTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Like Button
                val activeColor = BiliPink
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick() }.padding(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icon_like_0),
                        contentDescription = "Like",
                        modifier = Modifier.size(14.dp),
                        tint = if (reply.liked) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (reply.likeCount > 0) "${reply.likeCount}" else "点赞",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (reply.liked) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Reply Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onReplyClick() }.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Reply",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "回复(${reply.childCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
            if (showReplyPreview && reply.childMsgList.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                val previewList = reply.childMsgList.take(3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onClick() }
                        .padding(8.dp)
                ) {
                    previewList.forEachIndexed { index, child ->
                        val childName = child.sender?.name ?: ""
                        val (richText, inlineContent) = parseRichText(child.message, child.emotes, child.members, resolvedB23Links)
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(childName)
                                }
                                append(": ")
                                append(richText)
                            },
                            inlineContent = inlineContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = if (index != previewList.lastIndex) 4.dp else 0.dp)
                        )
                    }
                }
            }
        }
    }

    WysAlertDialog(
        show = showDeleteConfirmDialog,
        title = "确认操作",
        content = {
            Text(
                text = if (isOwnComment) "确定要删除这条评论吗？" else stringResource(R.string.clear_reply_warning),
                textAlign = TextAlign.Center
            )
        },
        onDismissRequest = { showDeleteConfirmDialog = false },
        onConfirm = {
            showDeleteConfirmDialog = false
            performAction()
        }
    )
}