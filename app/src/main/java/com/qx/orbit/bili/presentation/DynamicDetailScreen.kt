package com.qx.orbit.bili.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.*
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.theme.BiliPink
import com.qx.orbit.bili.presentation.viewmodel.DynamicDetailViewModel
import com.qx.orbit.bili.util.formatCount
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@Composable
fun DynamicDetailScreen(
    dynamicId: String,
    navController: NavHostController,
    viewModel: DynamicDetailViewModel = viewModel()
) {
    val dynamic by viewModel.dynamic.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val isReplyLoading by viewModel.isReplyLoading.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val emotes by viewModel.emotes.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val behavior = RotaryScrollableDefaults.behavior(listState)
    val focusRequester = remember { FocusRequester() }
    
    

    
    val context = LocalContext.current
    var showImageDialog by remember { mutableStateOf<String?>(null) }
    var showWriteReply by remember { mutableStateOf(false) }

    LaunchedEffect(dynamicId) {
        viewModel.loadDynamic(dynamicId)
        viewModel.loadReplies()
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    if (showImageDialog != null) {
        ImageViewerDialog(imageUrl = showImageDialog!!, onDismiss = { showImageDialog = null })
    }

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.focusRequester(focusRequester)
    ) { contentPadding ->
        if (isLoading && dynamic == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && dynamic == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = error ?: "加载失败", color = Color.White)
            }
        } else {
            dynamic?.let { item ->
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().rotaryScrollable(behavior, focusRequester),
                    contentPadding = contentPadding
                ) {
                    item {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                                        item.userInfo?.mid?.let { mid -> navController.navigate("user_space/$mid") }
                                    }
                                ) {
                                    UserAvatar(
                                        avatarUrl = item.userInfo?.avatar ?: "",
                                        officialRole = item.userInfo?.official ?: 0,
                                        modifier = Modifier.size(32.dp),
                                        isVip = (item.userInfo?.vip_role ?: 0) > 0
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        UserNameText(
                                            name = item.userInfo?.name ?: "Unknown",
                                            isVip = (item.userInfo?.vip_role ?: 0) > 0,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (item.major_type == "MAJOR_TYPE_ARCHIVE") "投稿了视频" else "发布了动态",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }



                                if (item.title.isNotEmpty()) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                if (item.content.isNotEmpty() && item.content != item.archiveTitle) {
                                    val (richText, inlineContent) = parseRichText(item.content, item.emotes, item.members, emptyMap())
                                    val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                                    Text(
                                        text = richText,
                                        inlineContent = inlineContent,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp).pointerInput(Unit) {
                                            detectTapGestures { offset ->
                                                val pos = textLayoutResult.value?.getOffsetForPosition(offset) ?: -1
                                                if (pos >= 0) {
                                                    richText.getStringAnnotations("USER", pos, pos).firstOrNull()?.let {
                                                        navController.navigate("user_space/${it.item}")
                                                    }
                                                }
                                            }
                                        },
                                        onTextLayout = { textLayoutResult.value = it }
                                    )
                                }

                                if (item.images.isNotEmpty()) {
                                    item.images.forEach { imgUrl ->
                                        val fixedUrl = when {
                                            imgUrl.startsWith("//") -> "https:$imgUrl"
                                            imgUrl.startsWith("http://") -> imgUrl.replaceFirst("http://", "https://")
                                            else -> imgUrl
                                        }
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(fixedUrl).crossfade(true).build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { showImageDialog = fixedUrl },
                                            contentScale = ContentScale.FillWidth
                                        )
                                    }
                                }

                                if (item.major_type == "MAJOR_TYPE_ARCHIVE" && item.cover.isNotEmpty()) {
                                    val fixedUrl = when {
                                        item.cover.startsWith("//") -> "https:${item.cover}"
                                        item.cover.startsWith("http://") -> item.cover.replaceFirst("http://", "https://")
                                        else -> item.cover
                                    }
                                    val finalUrl = if (!fixedUrl.contains("@")) "$fixedUrl@480w_270h_1c.webp" else fixedUrl
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable {
                                            if (item.bvid.isNotEmpty() || item.comment_id > 0) {
                                                navController.navigate("detail/${item.bvid}/${item.comment_id}")
                                            }
                                        }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(finalUrl).crossfade(true).build(),
                                            contentDescription = null,
                                            modifier = Modifier.width(80.dp).height(50.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.archiveTitle,
                                            fontSize = 12.sp,
                                            color = Color.LightGray,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                item.dynamic_forward?.let { forward ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (forward.major_type == "MAJOR_TYPE_ARCHIVE" && (forward.bvid.isNotEmpty() || forward.comment_id > 0)) {
                                                    navController.navigate("detail/${forward.bvid}/${forward.comment_id}")
                                                } else if (forward.dynamicId.isNotEmpty()) {
                                                    navController.navigate("dynamic_detail/${forward.dynamicId}")
                                                }
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "@${forward.userInfo?.name ?: "已失效动态"}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64B5F6),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        if (forward.content.isNotEmpty() && forward.content != forward.archiveTitle) {
                                            val (fwdRichText, fwdInlineContent) = parseRichText(forward.content, forward.emotes, forward.members, emptyMap())
                                            Text(
                                                text = fwdRichText,
                                                inlineContent = fwdInlineContent,
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                                            )
                                        }
                                        if (forward.major_type == "MAJOR_TYPE_ARCHIVE" && forward.cover.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val fwdCoverUrl = when {
                                                    forward.cover.startsWith("//") -> "https:${forward.cover}"
                                                    forward.cover.startsWith("http://") -> forward.cover.replaceFirst("http://", "https://")
                                                    else -> forward.cover
                                                }
                                                val fwdFinalUrl = if (!fwdCoverUrl.contains("@")) "$fwdCoverUrl@240w_135h_1c.webp" else fwdCoverUrl
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context).data(fwdFinalUrl).crossfade(true).build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = forward.archiveTitle,
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (forward.images.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                forward.images.take(3).forEach { imgUrl ->
                                                    val fixedImgUrl = when {
                                                        imgUrl.startsWith("//") -> "https:$imgUrl"
                                                        imgUrl.startsWith("http://") -> imgUrl.replaceFirst("http://", "https://")
                                                        else -> imgUrl
                                                    }
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context).data(fixedImgUrl).crossfade(true).build(),
                                                        contentDescription = null,
                                                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable { showImageDialog = fixedImgUrl },
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    val isLiked = item.stats?.liked == true
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { viewModel.toggleLike() }.padding(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.icon_like_0),
                                            contentDescription = "Like",
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isLiked) BiliPink else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val likeCount = item.stats?.like ?: 0
                                        Text(
                                            text = formatCount(likeCount),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                            color = if (isLiked) BiliPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { /* Share */ }.padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Share",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val shareCount = item.stats?.share ?: 0
                                        Text(
                                            text = formatCount(shareCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            viewModel.loadEmotes()
                                            showWriteReply = true
                                        }.padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Reply",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val replyCount = item.stats?.reply ?: 0
                                        Text(
                                            text = formatCount(replyCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
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

                    if (replies.isNotEmpty()) {
                        item {
                            ListHeader { Text("评论区") }
                        }
                        
                        items(replies.size) { index ->
                            if (index == replies.size - 3) {
                                LaunchedEffect(index) { viewModel.loadReplies() }
                            }
                            ReplyCard(
                                reply = replies[index],
                                transformation = SurfaceTransformation(transformationSpec),
                                modifier = Modifier.transformedHeight(this, transformationSpec),
                                navController = navController,
                                onLikeClick = { viewModel.likeReply(replies[index].rpid, replies[index].liked) }
                            )
                        }
                    }

                    if (isReplyLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    WriteReplyScreen(
        visible = showWriteReply,
        targetName = null,
        emotes = emotes,
        onSend = { text ->
            viewModel.sendReply(
                text = text,
                onSuccess = { showWriteReply = false },
                onError = { error ->
                    showWriteReply = false
                    com.qx.orbit.bili.presentation.ui.components.RoundToast.show(context, error)
                }
            )
        },
        onClose = { showWriteReply = false }
    )
}
