package com.qx.orbit.bili.presentation

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
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
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.api.ReplyApi
import com.qx.orbit.bili.data.api.UserInfoApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.StringUtil
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.model.VideoInfo
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.ReplyCard
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.presentation.viewmodel.VideoDetailViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.formatCount
import com.qx.orbit.bili.util.VideoDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoDetailScreen(navController: NavHostController, bvid: String, aid: Long, viewModel: VideoDetailViewModel = viewModel()) {
    LaunchedEffect(bvid, aid) {
        viewModel.loadData(bvid, aid)
    }
    
    val context = LocalContext.current

    val videoInfo by viewModel.videoInfo.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val replyCount by viewModel.replyCount.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val replyErrorMessage by viewModel.replyErrorMessage.collectAsState()
    var showCoinDialog by remember { mutableStateOf(false) }
    var showFavDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showTripleSuccessOverlay by remember { mutableStateOf(false) }
    var cacheQualities by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isFetchingQualities by remember { mutableStateOf(false) }
    
    val emotes by viewModel.emotes.collectAsState()
    val isEmoteLoading by viewModel.isEmoteLoading.collectAsState()
    var showWriteReply by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<Reply?>(null) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    // Focus requesters for rotary input for each page
    val focusRequesters = remember { List(3) { FocusRequester() } }
    
    LaunchedEffect(pagerState.currentPage) {
        try {
            focusRequesters[pagerState.currentPage].requestFocus()
        } catch (e: Exception) {
            // Ignore focus exceptions
        }
    }
    
    var dynamicColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    val defaultColorScheme = MaterialTheme.colorScheme
    
    LaunchedEffect(videoInfo) {
        val rawCover = videoInfo?.cover ?: ""
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
    // Cache Dialog
    Dialog(
        visible = showCacheDialog,
        onDismissRequest = { showCacheDialog = false }
    ) {
        if (isFetchingQualities) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            TransformingLazyColumn(state = listState, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp), rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                item {
                    ListHeader(
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("选择清晰度", color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(cacheQualities) { (name, qn) ->
                    Button(
                        onClick = {
                            val info = videoInfo
                            if (info != null) {
                                VideoDownloadManager.enqueue(
                                    url = "", // Will be fetched inside manager
                                    title = info.title,
                                    filename = "${info.title}_$qn.mp4",
                                    context = context,
                                    aid = aid,
                                    cid = info.cids.firstOrNull() ?: 0L,
                                    bvid = bvid,
                                    qn = qn,
                                    type = "MP4",
                                    coverUrl = info.cover.replace("http://", "https://"),
                                    duration = StringUtil.parseTime(info.duration)
                                )
                                RoundToast.show(context, "正在下载视频...")
                                showCacheDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(name)
                    }
                }
                item {
                    Button(
                        onClick = {
                            val info = videoInfo
                            if (info != null) {
                                VideoDownloadManager.enqueue(
                                    url = "",
                                    title = info.title,
                                    filename = "${info.title}_audio.m4s",
                                    context = context,
                                    aid = aid,
                                    cid = info.cids.firstOrNull() ?: 0L,
                                    bvid = bvid,
                                    qn = 16,
                                    type = "AUDIO_AND_SUBTITLE",
                                    coverUrl = info.cover.replace("http://", "https://"),
                                    duration = StringUtil.parseTime(info.duration)
                                )
                                RoundToast.show(context, "正在下载视频...")
                                showCacheDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("仅音频+字幕")
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = isLoading && videoInfo == null,
                transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
                label = "LoadingAnimation"
            ) { isInitialLoading ->
                if (isInitialLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null && videoInfo == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.loadData(bvid, aid) },
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
                        }
                    }
                } else {
                val context = LocalContext.current
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                    when (page) {
                        0 -> VideoInfoPage(
                            videoInfo = videoInfo, 
                            tags = tags, 
                            focusRequester = focusRequesters[0],
                            onBackClick = { navController.popBackStack() },
                            onPlayClick = {
                                val info = videoInfo ?: return@VideoInfoPage
                                val qn = SharedPreferencesUtil.getInt("play_qn", 16)
                                val playerData = PlayerData(
                                    title = info.title,
                                    aid = aid,
                                    cid = info.cids.firstOrNull() ?: 0L,
                                    cids = info.cids,
                                    pagenames = info.pagenames,
                                    type = if (info.epid > 0) PlayerData.TYPE_BANGUMI else PlayerData.TYPE_VIDEO,
                                    qn = qn
                                )
                                val jsonStr = Gson().toJson(playerData)
                                val encodedJson = URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.toString())
                                navController.navigate("player/$encodedJson")
                            },
                            onLikeClick = { viewModel.toggleLike { success, msg -> RoundToast.show(context, msg) } },
                            onTripleClick = {
                                viewModel.doTriple { success, msg ->
                                    if (success) {
                                        showTripleSuccessOverlay = true
                                    } else {
                                        RoundToast.show(context, msg)
                                    }
                                }
                            },
                            onCoinClick = { showCoinDialog = true },
                            onFavClick = { 
                                viewModel.loadFavoriteFolders()
                                showFavDialog = true
                            },
                            onUpClick = { mid -> navController.navigate("user_space/$mid") },
                            onCacheClick = {
                                showCacheDialog = true
                                if (cacheQualities.isEmpty()) {
                                    isFetchingQualities = true
                                    viewModel.viewModelScope.launch {
                                        val info = videoInfo
                                        if (info != null) {
                                            val pd = PlayerApi.getVideo(PlayerData(aid = aid, cid = info.cids.firstOrNull() ?: 0L, qn = 112))
                                            val names = pd.qnStrList
                                            val vals = pd.qnValueList
                                            if (names != null && vals != null && names.size == vals.size) {
                                                cacheQualities = names.zip(vals.toTypedArray())
                                            }
                                        }
                                        isFetchingQualities = false
                                    }
                                }
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
                        2 -> VideoRelatedPage(
                            relatedVideos = relatedVideos, 
                            focusRequester = focusRequesters[2],
                            navController = navController
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
                onSuccess = {
                    showWriteReply = false
                },
                onError = { error ->
                    showWriteReply = false
                    RoundToast.show(context, error)
                }
            )
        },
        onClose = { showWriteReply = false }
    )
    
    var currentCoins by remember { mutableIntStateOf(-1) }
    LaunchedEffect(showCoinDialog) {
        if (showCoinDialog) {
            try {
                currentCoins = UserInfoApi.getCurrentUserCoin()
            } catch (e: Exception) {}
        }
    }
    
    Dialog(visible = showCoinDialog, onDismissRequest = { showCoinDialog = false }) {
        var selectedCoinIndex by remember { mutableIntStateOf(0) }
        val rowOffsetX by animateDpAsState(targetValue = if (selectedCoinIndex == 0) 45.dp else (-45).dp)
        
        val scaleBox1 by animateFloatAsState(targetValue = if (selectedCoinIndex == 0) 1f else 0.8f)
        val scaleBox2 by animateFloatAsState(targetValue = if (selectedCoinIndex == 1) 1f else 0.8f)
        
        val dragYDpAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        val blockJumpAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        var isHit by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        
        LaunchedEffect(isHit) {
            if (isHit) {
                blockJumpAnim.animateTo(-15f, tween(100))
                blockJumpAnim.animateTo(0f, tween(100))
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (!isHit) {
                                    if (dragAmount < -10) selectedCoinIndex = 1
                                    else if (dragAmount > 10) selectedCoinIndex = 0
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.offset(x = rowOffsetX),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp).offset(y = if (selectedCoinIndex == 0) blockJumpAnim.value.dp else 0.dp).graphicsLayer { scaleX = scaleBox1; scaleY = scaleBox1 }) {
                            Image(
                                painter = painterResource(R.drawable.ic_pay_coins_box),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp)
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_coins_one),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp).offset(y = if (selectedCoinIndex == 1) blockJumpAnim.value.dp else 0.dp).graphicsLayer { scaleX = scaleBox2; scaleY = scaleBox2 }) {
                            Image(
                                painter = painterResource(R.drawable.ic_pay_coins_box),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp)
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_coins_two),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Image(
                    painter = painterResource(if (selectedCoinIndex == 0) R.drawable.ic_22_mario else R.drawable.ic_22_gun_sister),
                    contentDescription = "2233",
                    modifier = Modifier
                        .size(72.dp)
                        .offset(y = dragYDpAnim.value.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (!isHit) {
                                    isHit = true
                                    coroutineScope.launch {
                                        dragYDpAnim.animateTo(-30f, tween(150))
                                        viewModel.doCoin(selectedCoinIndex + 1) { success, msg -> RoundToast.show(context, msg) }
                                        dragYDpAnim.animateTo(0f, tween(150))
                                        delay(100.milliseconds)
                                        showCoinDialog = false
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (!isHit) {
                                        coroutineScope.launch {
                                            dragYDpAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (!isHit) {
                                        val dragDp = with(density) { dragAmount.toDp().value }
                                        coroutineScope.launch {
                                            val nextY = (dragYDpAnim.value + dragDp).coerceAtMost(0f)
                                            if (nextY <= -30f && dragYDpAnim.value > -30f) {
                                                isHit = true
                                                dragYDpAnim.snapTo(-30f)
                                                viewModel.doCoin(selectedCoinIndex + 1) { success, msg -> RoundToast.show(context, msg) }
                                                dragYDpAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                delay(100.milliseconds)
                                                showCoinDialog = false
                                            } else if (nextY > -30f) {
                                                dragYDpAnim.snapTo(nextY)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (currentCoins >= 0) "剩余硬币: $currentCoins" else "剩余硬币: 获取中...",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
    
    val folders by viewModel.favoriteFolders.collectAsState()
    Dialog(visible = showFavDialog, onDismissRequest = { showFavDialog = false }) {
            val listState = rememberTransformingLazyColumnState()
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { focusRequester.requestFocus() } catch (e: Exception) {}
            }
            ScreenScaffold(
                scrollState = listState,
                modifier = Modifier.focusRequester(focusRequester).focusable()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (folders == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (folders!!.isEmpty()) {
                        Text("暂无收藏夹", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                    } else {
                        TransformingLazyColumn(state = listState, contentPadding = PaddingValues(16.dp), rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                            item { ListHeader { Text("选择收藏夹", color = MaterialTheme.colorScheme.primary) } }
                            items(folders!!.size) { index ->
                                val folder = folders!![index]
                                Button(
                                    onClick = { 
                                        viewModel.doFavorite(folder.id) { success, msg -> RoundToast.show(context, msg) }
                                        showFavDialog = false 
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (folder.isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (folder.isFav) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${folder.mediaCount} 个视频",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (folder.isFav) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = showTripleSuccessOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            LaunchedEffect(showTripleSuccessOverlay) {
                if (showTripleSuccessOverlay) {
                    delay(3000)
                    showTripleSuccessOverlay = false
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showTripleSuccessOverlay = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.bili_2233_heart),
                        contentDescription = "Triple Success",
                        modifier = Modifier.height(100.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "三连成功，感谢推荐！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        }
}
@Composable
fun VideoInfoPage(
    videoInfo: VideoInfo?, 
    tags: String, 
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onLikeClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavClick: () -> Unit,
    onCacheClick: () -> Unit,
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
            if (videoInfo != null) {
                // 1. Cover Image
                item {
                    val secureCover = videoInfo.cover.replace("http://", "https://")
                    val coverUrl = if (secureCover.contains("@")) secureCover else "${secureCover}@480w_270h_1c.webp"
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
                
                // 2. Title (slightly larger)
                item {
                    Text(
                        text = videoInfo.title,
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
                    if (videoInfo.staff.isNotEmpty()) {
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
                            items(videoInfo.staff) { upInfo ->
                                Row(
                                    modifier = Modifier
                                        .then(if (videoInfo.staff.size == 1) Modifier.fillParentMaxWidth() else Modifier)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable(onClick = { onUpClick(upInfo.mid) })
                                        .padding(start = 6.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        avatarUrl = upInfo.avatar,
                                        officialRole = upInfo.official,
                                        modifier = Modifier.size(36.dp),
                                        isVip = upInfo.vip_role > 0
                                    )
                                    
                                    Spacer(Modifier.width(8.dp))
                                    
                                    val roleText = upInfo.sign.ifEmpty { "参演" }
                                    Column(verticalArrangement = Arrangement.Center) {
                                        UserNameText(
                                            name = upInfo.name,
                                            isVip = upInfo.vip_role > 0,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = roleText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
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
                                text = formatCount(videoInfo.stats?.view ?: 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painterResource(R.drawable.ic_danmaku),
                                contentDescription = "Danmaku",
                                modifier = Modifier.height(14.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatCount(videoInfo.stats?.danmaku ?: 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically){
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = "Views",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = videoInfo.timeDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically){
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = "Views",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = videoInfo.bvid,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }


                    }
                }
                
                // 5. Tags (Merged into Description)
                
                // 5.5 Description
                item {
                    if (videoInfo.description.isNotEmpty() || tags.isNotEmpty()) {
                        val fullDesc = buildAnnotatedString {
                            if (videoInfo.description.isNotEmpty()) {
                                append(videoInfo.description)
                            }
                            if (tags.isNotEmpty()) {
                                if (videoInfo.description.isNotEmpty()) {
                                    append("\n")
                                }
                                val formattedTags = tags.split("/").joinToString(" ") { "#${it.trim()}" }
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(formattedTags)
                                }
                            }
                        }
                        
                        Text(
                            text = fullDesc,
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
                            .fillMaxWidth().padding(horizontal = 8.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        icon = {Icon(imageVector = Icons.Filled.PlayCircleOutline, contentDescription = null)},
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("播放视频")
                    }
                }
                item { Spacer(modifier = Modifier.height(1.dp)) }
                // 7. Triple Actions
                item {
                    val isLiked = videoInfo.stats?.liked == true
                    val isCoined = (videoInfo.stats?.coined ?: 0) > 0
                    val isFav = videoInfo.stats?.favoured == true
                    
                    val likeInteractionSource = remember { MutableInteractionSource() }
                    val coinInteractionSource = remember { MutableInteractionSource() }
                    val favInteractionSource = remember { MutableInteractionSource() }
                    
                    val isLikePressed by likeInteractionSource.collectIsPressedAsState()
                    val tripleProgress = remember { Animatable(0f) }
                    val view = LocalView.current
                    
                    LaunchedEffect(isLikePressed) {
                        if (isLikePressed) {
                            delay(300)
                            tripleProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                            )
                            if (tripleProgress.value >= 1f) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                delay(80)
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onTripleClick()
                            }
                        } else {
                            if (tripleProgress.value > 0f && tripleProgress.value < 1f) {
                                tripleProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = (tripleProgress.value * 1000).toInt().coerceAtLeast(100),
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            } else if (tripleProgress.value >= 1f) {
                                tripleProgress.snapTo(0f)
                            }
                        }
                    }

                    // Calculate color and shake based on tripleProgress
                    val p = tripleProgress.value
                    val amplitude = if (p < 0.9f) 4f * p else 4f * (1f - p) * 10f
                    val shakeOffset = if (p > 0f && p < 1f) (kotlin.math.sin(p * Math.PI * 50) * amplitude).toFloat().dp else 0.dp
                    val blendedContainerColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary, p)
                    val blendedContentColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onPrimary, p)

                    val likeCornerRadius by animateDpAsState(if (isLiked) 16.dp else 26.dp, label = "likeShape")
                    val coinCornerRadius by animateDpAsState(if (isCoined) 16.dp else 26.dp, label = "coinShape")
                    val favCornerRadius by animateDpAsState(if (isFav) 16.dp else 26.dp, label = "favShape")

                    val baseLikeContainerColor by animateColorAsState(if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "likeColor")
                    val baseCoinContainerColor by animateColorAsState(if (isCoined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "coinColor")
                    val baseFavContainerColor by animateColorAsState(if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "favColor")

                    val baseLikeContentColor by animateColorAsState(if (isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "likeContentColor")
                    val baseCoinContentColor by animateColorAsState(if (isCoined) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "coinContentColor")
                    val baseFavContentColor by animateColorAsState(if (isFav) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "favContentColor")
                    
                    val likeContainerColor = if (p > 0f && !isLiked) blendedContainerColor else baseLikeContainerColor
                    val coinContainerColor = if (p > 0f && !isCoined) blendedContainerColor else baseCoinContainerColor
                    val favContainerColor = if (p > 0f && !isFav) blendedContainerColor else baseFavContainerColor
                    
                    val likeContentColor = if (p > 0f && !isLiked) blendedContentColor else baseLikeContentColor
                    val coinContentColor = if (p > 0f && !isCoined) blendedContentColor else baseCoinContentColor
                    val favContentColor = if (p > 0f && !isFav) blendedContentColor else baseFavContentColor

                    ButtonGroup(
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
                    ) {
                        FilledIconButton(
                            onClick = { if (tripleProgress.value == 0f) onLikeClick() },
                            interactionSource = likeInteractionSource,
                            modifier = Modifier.animateWidth(likeInteractionSource).size(52.dp),
                            shapes = IconButtonDefaults.animatedShapes(shape = RoundedCornerShape(likeCornerRadius)),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = likeContainerColor,
                                contentColor = likeContentColor
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(id = R.drawable.icon_like_0), 
                                    contentDescription = "Like",
                                    modifier = Modifier.offset(x = shakeOffset, y = shakeOffset)
                                )
                                Text(
                                    text = formatCount(videoInfo.stats?.like ?: 0),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                                )
                            }
                        }
                        
                        FilledIconButton(
                            onClick = onCoinClick,
                            interactionSource = coinInteractionSource,
                            modifier = Modifier.animateWidth(coinInteractionSource).size(52.dp),
                            shapes = IconButtonDefaults.animatedShapes(shape = RoundedCornerShape(coinCornerRadius)),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = coinContainerColor,
                                contentColor = coinContentColor
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(id = R.drawable.icon_coin_0), 
                                    contentDescription = "Coin",
                                    modifier = Modifier.offset(x = shakeOffset, y = shakeOffset)
                                )
                                Text(
                                    text = formatCount(videoInfo.stats?.coin ?: 0),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                                )
                            }
                        }
                        
                        FilledIconButton(
                            onClick = onFavClick,
                            interactionSource = favInteractionSource,
                            modifier = Modifier.animateWidth(favInteractionSource).size(52.dp),
                            shapes = IconButtonDefaults.animatedShapes(shape = RoundedCornerShape(favCornerRadius)),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = favContainerColor,
                                contentColor = favContentColor
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(id = R.drawable.icon_fav_0), 
                                    contentDescription = "Fav",
                                    modifier = Modifier.offset(x = shakeOffset, y = shakeOffset)
                                )
                                Text(
                                    text = formatCount(videoInfo.stats?.favorite ?: 0),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(1.dp)) }
                
                // Cache Button
                item {
                    Button(
                        onClick = onCacheClick,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .fillMaxWidth().padding(horizontal = 8.dp),
                        icon = { Icon(Icons.Default.Download, contentDescription = "Cache", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) },
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("缓存")
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }

            }
        }
    }
}

@Composable
fun VideoCommentsPage(
    replies: List<Reply>, 
    replyCount: Int,
    replyErrorMessage: String?,
    focusRequester: FocusRequester,
    navController: NavHostController,
    onLoadMore: () -> Unit,
    onClick: (Reply) -> Unit,
    onLikeClick: (Reply) -> Unit,
    onReplyClick: (Reply) -> Unit,
    onSendCommentClick: () -> Unit,
    onRemove: (Reply) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState, modifier = Modifier.focusRequester(focusRequester)) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader{
                    Text(
                        "评论(${formatCount(replyCount)})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Button(
                    onClick = onSendCommentClick,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = {Icon(imageVector = Icons.Filled.Edit, contentDescription = null)},
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("发布评论", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (replyErrorMessage != null && replies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onLoadMore() },
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
                        }
                    }
                }
            } else {
                items(replies.size) { index ->
                    if (index == replies.size - 1) {
                        LaunchedEffect(index) { onLoadMore() }
                    }
                    ReplyCard(
                        reply = replies[index],
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.animateItem().transformedHeight(this, transformationSpec),
                        navController = navController,
                        replyType = ReplyApi.REPLY_TYPE_VIDEO,
                        onRemove = { onRemove(replies[index]) },
                        onLikeClick = { onLikeClick(replies[index]) },
                        onClick = { onClick(replies[index]) },
                        onReplyClick = { onReplyClick(replies[index]) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoRelatedPage(
    relatedVideos: List<VideoCard>, 
    focusRequester: FocusRequester,
    navController: NavHostController
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
                        "相关推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(relatedVideos.size) { index ->
                RecommendVideoCard(
                    item = relatedVideos[index],
                    onClick = { 
                        navController.navigate("detail/${relatedVideos[index].bvid}/${relatedVideos[index].aid}")
                    },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
        }
    }
}
