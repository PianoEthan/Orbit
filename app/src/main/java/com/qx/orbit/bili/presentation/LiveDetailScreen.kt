package com.qx.orbit.bili.presentation

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import com.qx.orbit.bili.presentation.theme.extractSeedColorFromBitmap
import com.qx.orbit.bili.presentation.theme.generateWearColorSchemeFromSeed
import com.qx.orbit.bili.presentation.theme.ActiveDynamicTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import androidx.compose.ui.res.painterResource
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.LiveRoom
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.presentation.viewmodel.EmoteInline
import com.qx.orbit.bili.presentation.viewmodel.LiveDetailViewModel
import com.qx.orbit.bili.util.formatCount
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveDetailScreen(
    roomId: Long,
    navController: NavHostController,
    viewModel: LiveDetailViewModel = viewModel()
) {
    LaunchedEffect(roomId) {
        viewModel.loadRoom(roomId)
    }

    val room by viewModel.room.collectAsState()
    val playInfo by viewModel.playInfo.collectAsState()
    val error by viewModel.error.collectAsState()
    val recommended by viewModel.recommended.collectAsState()
    val danmakuList by viewModel.danmakuList.collectAsState()
    val danmakuCount by viewModel.danmakuCount.collectAsState()
    val emotes by viewModel.emotes.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val focusRequesters = remember { List(3) { FocusRequester() } }
    var showWriteReply by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        try {
            focusRequesters[pagerState.currentPage].requestFocus()
        } catch (_: Exception) {}
    }
    
    var dynamicColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    val defaultColorScheme = MaterialTheme.colorScheme
    
    LaunchedEffect(room) {
        val rawCover = room?.cover ?: ""
        if (rawCover.isNotEmpty()) {
            val secureCover = rawCover.replace("http://", "https://")
            val coverUrl = if (secureCover.contains("@")) secureCover else "${secureCover}@128w_128h_1c.webp"
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(128)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is coil.request.SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val seedColor = extractSeedColorFromBitmap(bitmap)
                    if (seedColor != null) {
                        dynamicColorScheme = generateWearColorSchemeFromSeed(seedColor, defaultColorScheme)
                        ActiveDynamicTheme.colorScheme = dynamicColorScheme
                    }
                }
            }
        }
    }

    MaterialTheme(colorScheme = dynamicColorScheme ?: defaultColorScheme) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (room) {
            null if error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "加载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadRoom(roomId) },
                            modifier = Modifier.size(width = 80.dp, height = 32.dp)
                        ) {
                            Text("重试", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> LiveInfoPage(
                                room = room!!,
                                focusRequester = focusRequesters[0],
                                onBackClick = { navController.popBackStack() },
                                onPlayClick = {
                                    val streamUrl = viewModel.getStreamUrl() ?: return@LiveInfoPage
                                    val liveStartTime = viewModel.getLiveStartTime()
                                    val playerData = PlayerData(
                                        title = "直播·${room!!.title}",
                                        videoUrl = streamUrl,
                                        aid = room!!.roomid,
                                        mid = room!!.uid,
                                        type = PlayerData.TYPE_LIVE,
                                        timeStamp = liveStartTime
                                    )
                                    val jsonStr = Gson().toJson(playerData)
                                    val encodedJson =
                                        URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.toString())
                                    navController.navigate("player/$encodedJson")
                                },
                                onUpClick = { mid ->
                                    navController.navigate("user_space/$mid")
                                }
                            )

                        1 -> LiveDanmakuPage(
                            danmakuList = danmakuList,
                            danmakuCount = danmakuCount,
                            focusRequester = focusRequesters[1],
                                onSendDanmaku = {
                                    viewModel.loadEmotes(roomId)
                                    showWriteReply = true
                                }
                            )

                            2 -> LiveRecommendPage(
                                recommended = recommended,
                                focusRequester = focusRequesters[2],
                                navController = navController,
                                onLoadMore = { viewModel.loadRecommended() }
                            )
                        }
                    }

                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
        WriteReplyScreen(
            visible = showWriteReply,
            targetName = null,
            emotes = emotes,
            isLive = true,
            onSend = { text ->
                viewModel.sendDanmaku(text, roomId) { ok, msg ->
                    if (ok) {
                        showWriteReply = false
                    } else {
                        android.widget.Toast.makeText(context, "发送失败: $msg", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSendEmote = { emoticonUnique ->
                viewModel.sendLiveEmote(emoticonUnique, roomId) { ok, msg ->
                    if (ok) {
                        showWriteReply = false
                    } else {
                        android.widget.Toast.makeText(context, "发送失败: $msg", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClose = { showWriteReply = false }
    )
    }
}

@Composable
fun LiveInfoPage(
    room: LiveRoom,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onUpClick: (Long) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    var isDescExpanded by remember { mutableStateOf(false) }

    ScreenScaffold(scrollState = listState, modifier = Modifier.focusRequester(focusRequester)) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = contentPadding
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clickable { onBackClick() }
                )
            }

            // 1. Cover Image
            item {
                val coverUrl = room.user_cover.ifEmpty { room.cover }.ifEmpty { room.keyframe }
                val secureCover = coverUrl.replace("http://", "https://")
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(secureCover)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                        }
                        .fillMaxWidth(0.95f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayClick() }
                )
            }

            // 2. Title
            item {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                        }
                        .fillMaxWidth()
                )
            }

            // 3. UP Info Capsule
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                        }
                        .fillMaxWidth()
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { onUpClick(room.uid) }
                                .padding(start = 6.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarUrl = room.face,
                                officialRole = 0,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                UserNameText(
                                    name = room.uname,
                                    isVip = false,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "主播",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 4. Small Metadata
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                        }
                        .fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = "Views",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${formatCount(room.online)}人观看",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = "Time",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        val area = buildString {
                            if (room.area_parent_name.isNotEmpty()) append(room.area_parent_name)
                            if (room.area_name.isNotEmpty()) {
                                if (isNotEmpty()) append(" > ")
                                append(room.area_name)
                            }
                        }
                        Text(
                            text = area.ifEmpty { "直播中" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // 5. Description
            item {
                if (room.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = room.description.replace(Regex("<[^>]+>"), "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                if (isRound) {
                                    with(transformationSpec) {
                                        applyContainerTransformation(scrollProgress)
                                    }
                                }
                            }
                            .fillMaxWidth()
                            .clickable { isDescExpanded = !isDescExpanded }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 6. Play Button
            item {
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                        }
                        .fillMaxWidth(),
                    icon = { Icon(imageVector = Icons.Filled.PlayCircleOutline, contentDescription = null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("观看直播")
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun LiveDanmakuPage(
    danmakuList: List<com.qx.orbit.bili.presentation.viewmodel.DanmakuMessage>,
    danmakuCount: Int,
    focusRequester: FocusRequester,
    onSendDanmaku: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val behavior = rememberSafeRotaryScrollableBehavior(listState)
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(scrollState = listState, modifier = Modifier.focusRequester(focusRequester)) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.rotaryScrollable(behavior, focusRequester),
            contentPadding = contentPadding
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader {
                    Text(
                        "弹幕 ($danmakuCount)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                            }
                        }
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Button(
                        onClick = onSendDanmaku,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("发送弹幕", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            items(danmakuList.size) { index ->
                val msg = danmakuList[danmakuList.size - 1 - index]
                val textColor = if (msg.isSystem) MaterialTheme.colorScheme.onSurfaceVariant
                else Color(msg.color or 0xFF000000.toInt())

                DanmakuMessageText(
                    msg = msg,
                    textColor = textColor,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            if (isRound) {
                                with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                            }
                        }
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }

            if (danmakuList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                if (isRound) {
                                    with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                                }
                            }
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "等待弹幕...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

private fun AnnotatedString.Builder.appendInlineContentId(id: String, placeholderText: String) {
    append(placeholderText)
    val end = length
    val start = end - placeholderText.length
    addStringAnnotation(INLINE_CONTENT_TAG, id, start, end)
}

private fun buildDanmakuAnnotatedText(text: String, emotes: Map<String, EmoteInline>?): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    if (emotes.isNullOrEmpty() || emotes.keys.none { text.contains(it) }) {
        return AnnotatedString(text) to emptyMap()
    }
    val normalized = emotes.keys.fold(text) { acc, key -> acc.replace("[$key]", key) }
    val sortedKeys = emotes.keys.sortedByDescending { it.length }
    val regex = Regex(sortedKeys.joinToString("|") { Regex.escape(it) })
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        regex.findAll(normalized).forEach { match ->
            if (match.range.first > lastIndex) {
                append(normalized.substring(lastIndex, match.range.first))
            }
            val key = match.value
            val emote = emotes[key] ?: return@forEach
            val id = "emote_${key.hashCode()}"
            inlineContent[id] = InlineTextContent(
                placeholder = Placeholder(
                    width = if (emote.width > 0 && emote.height > 0) 1.2.em * emote.width / emote.height else 1.2.em,
                    height = 1.2.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(emote.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = key,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.akari),
                    onError = { Log.e("DanmakuEmote", "Inline emote load failed: $key url=${emote.url}", it.result.throwable) }
                )
            }
            appendInlineContentId(id, key)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < normalized.length) {
            append(normalized.substring(lastIndex))
        }
    }
    return annotated to inlineContent
}

@Composable
private fun DanmakuMessageText(
    msg: com.qx.orbit.bili.presentation.viewmodel.DanmakuMessage,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val single = msg.singleEmote
    if (single != null) {
        val senderName = msg.text.substringBefore("：", "").ifEmpty { msg.text.substringBefore(":", "") }
        val annotated = buildAnnotatedString {
            if (senderName.isNotEmpty()) {
                append(senderName)
                append("：")
            }
            appendInlineContentId("single_emote", "[emote]")
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = modifier,
            lineHeight = TextUnit.Unspecified,
            inlineContent = mapOf(
            "single_emote" to InlineTextContent(
                placeholder = Placeholder(
                    width = if (single.width > 0 && single.height > 0) {
                        (2.5f * single.width.toFloat() / single.height).em
                    } else 2.5.em,
                    height = 2.5.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(single.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "emote",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.bili_2233_fail),
                    onError = { Log.e("DanmakuEmote", "Single emote load failed: ${single.url}", it.result.throwable) }
                )
            }
            )
        )
        return
    }
    val text = msg.text
    val (annotated, inline) = remember(text, msg.emotes) { buildDanmakuAnnotatedText(text, msg.emotes) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
        modifier = modifier,
        lineHeight = TextUnit.Unspecified,
        inlineContent = inline
    )
}

@Composable
fun LiveRecommendPage(
    recommended: List<LiveRoom>,
    focusRequester: FocusRequester,
    navController: NavHostController,
    onLoadMore: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    ScreenScaffold(scrollState = listState, modifier = Modifier.focusRequester(focusRequester)) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader {
                    Text(
                        "推荐直播",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(recommended.size) { index ->
                if (index == recommended.size - 3) {
                    LaunchedEffect(index) { onLoadMore() }
                }
                val liveRoom = recommended[index]
                LiveRoomCard(
                    item = liveRoom,
                    transformation = SurfaceTransformation(transformationSpec),
                    onClick = {
                        navController.navigate("live_room/${liveRoom.roomid}")
                    },
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

private fun fixUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> url.replaceFirst("http://", "https://")
    else -> url
}
