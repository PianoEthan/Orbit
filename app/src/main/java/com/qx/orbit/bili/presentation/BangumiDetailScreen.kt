package com.qx.orbit.bili.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
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
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.Bangumi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.BangumiDetailViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BangumiDetailScreen(navController: NavHostController, mediaId: Long, viewModel: BangumiDetailViewModel = viewModel()) {
    LaunchedEffect(mediaId) {
        viewModel.loadData(mediaId)
    }
    
    val context = LocalContext.current

    val bangumiInfo by viewModel.bangumiInfo.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val replyCount by viewModel.replyCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val replyErrorMessage by viewModel.replyErrorMessage.collectAsState()
    
    val emotes by viewModel.emotes.collectAsState()
    val isEmoteLoading by viewModel.isEmoteLoading.collectAsState()
    var showWriteReply by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<Reply?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    val focusRequesters = remember { List(2) { FocusRequester() } }
    
    LaunchedEffect(pagerState.currentPage) {
        try {
            focusRequesters[pagerState.currentPage].requestFocus()
        } catch (e: Exception) {}
    }
    
    var dynamicColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    val defaultColorScheme = MaterialTheme.colorScheme
    
    LaunchedEffect(bangumiInfo) {
        val info = bangumiInfo?.info
        val rawCover = info?.cover ?: ""
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
        AnimatedContent(
            targetState = isLoading && bangumiInfo == null,
            transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
            label = "LoadingAnimation"
        ) { isInitialLoading ->
            if (isInitialLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null && bangumiInfo == null) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable { viewModel.loadData(mediaId) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.bili_2233_fail),
                            contentDescription = "Error",
                            modifier = Modifier.fillMaxWidth().offset(y = (-15).dp)
                        )
                        Text(
                            text = "加载失败，点击重试",
                            modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!errorMessage.isNullOrEmpty()) {
                            Text(
                                text = errorMessage ?: "",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            0 -> BangumiInfoPage(
                                bangumi = bangumiInfo, 
                                focusRequester = focusRequesters[0],
                                onBackClick = { navController.popBackStack() },
                                onEpisodeClick = { ep ->
                                    val info = bangumiInfo?.info ?: return@BangumiInfoPage
                                    val qn = SharedPreferencesUtil.getInt("play_qn", 16)
                                    val epLongTitle = ep.title_long ?: ""
                                    val epTitle = ep.title ?: ""
                                    val playerData = PlayerData(
                                        title = epLongTitle.ifEmpty { epTitle },
                                        aid = ep.aid,
                                        cid = ep.cid,
                                        cids = listOf(ep.cid),
                                        pagenames = listOf(epLongTitle),
                                        type = PlayerData.TYPE_BANGUMI,
                                        qn = qn,
                                        bvid = ""
                                    )
                                    val jsonStr = Gson().toJson(playerData)
                                    val encodedJson = URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.toString())
                                    navController.navigate("player/$encodedJson")
                                }
                            )
                            1 -> VideoCommentsPage(
                                replies = replies, 
                                replyCount = replyCount,
                                replyErrorMessage = replyErrorMessage,
                                focusRequester = focusRequesters[1],
                                navController = navController,
                                onLoadMore = { viewModel.loadReplies() },
                                onClick = { reply ->
                                    val json = Gson().toJson(reply)
                                    val encoded = URLEncoder.encode(json, "UTF-8")
                                    navController.navigate("reply_detail/${reply.rpid}/$encoded")
                                },
                                onLikeClick = { reply -> viewModel.likeReply(reply.rpid, reply.liked) },
                                onReplyClick = { reply ->
                                    replyTarget = reply
                                    viewModel.loadEmotes()
                                    showWriteReply = true
                                },
                                onSendCommentClick = {
                                    replyTarget = null
                                    viewModel.loadEmotes()
                                    showWriteReply = true
                                },
                                onRemove = { reply -> viewModel.removeReplyLocally(reply) }
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
        targetName = replyTarget?.sender?.name,
        emotes = emotes,
        onSend = { text ->
            viewModel.sendReply(
                text = text,
                root = replyTarget?.root?.takeIf { it > 0 } ?: replyTarget?.rpid ?: 0L,
                parent = replyTarget?.rpid ?: 0L,
                onSuccess = { showWriteReply = false },
                onError = { error ->
                    showWriteReply = false
                    RoundToast.show(context, error)
                }
            )
        },
        onClose = { showWriteReply = false }
    )
    }
}

@Composable
fun BangumiInfoPage(
    bangumi: Bangumi?, 
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onEpisodeClick: (Bangumi.Episode) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound
    var isDescExpanded by remember { mutableStateOf(false) }
    
    val info = bangumi?.info
    val rawCover = info?.cover ?: ""
    val secureCover = rawCover.replace("http://", "https://")
    val coverUrl = if (secureCover.contains("@")) secureCover else "${secureCover}@480w_640h_1c.webp"

    ScreenScaffold(scrollState = listState, modifier = Modifier.focusRequester(focusRequester)) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
                        .graphicsLayer { alpha = 0.6f }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val headerItem = listState.layoutInfo.visibleItems.find { it.index == 1 }
                        alpha = if (headerItem != null) {
                            val scrolled = -headerItem.offset.toFloat()
                            (scrolled / 200f).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                    .background(Color.Black)
            )

            TransformingLazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = contentPadding, 
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item { 
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clickable { onBackClick() }
                    ) 
                }
            
            if (info != null) {
                item {

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                if (isRound) {
                                    with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                                }
                            }
                            .fillMaxWidth(0.5f)
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                
                item {
                    Text(
                        text = bangumi.info.title ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                if (isRound) {
                                    with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                                }
                            }
                            .fillMaxWidth()
                    )
                }

                item {
                    val pubTime = bangumi.info.publish?.pub_time?.take(4)?.plus("年") ?: ""
                    Text(
                        text = "${bangumi.info.area_name ?: ""} · $pubTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                if (isRound) {
                                    with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                                }
                            }
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                val evaluateText = bangumi.info.evaluate ?: ""
                if (evaluateText.isNotEmpty()) {
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
                                //.padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { isDescExpanded = !isDescExpanded }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = evaluateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (bangumi.sectionList.isNotEmpty()) {
                    item {
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text("选集")
                        }
                    }
                    
                    val episodes = bangumi.sectionList.firstOrNull()?.episodes ?: emptyList()
                    items(episodes) { ep ->
                        val hasDistinctLongTitle = !ep.title_long.isNullOrEmpty() && ep.title_long != ep.title
                        Button(
                            onClick = { onEpisodeClick(ep) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer, 
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            icon = if (hasDistinctLongTitle) {
                                {
                                    Text(
                                        text = ep.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                null
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val longTitle = ep.title_long ?: ""
                                    Text(
                                        text = longTitle.takeIf { it.isNotEmpty() } ?: ep.title ?: "",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 2000
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
        }
    }
}
