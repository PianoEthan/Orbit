package com.qx.orbit.bili.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.Bangumi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.presentation.theme.ActiveDynamicTheme
import com.qx.orbit.bili.presentation.theme.extractSeedColorFromBitmap
import com.qx.orbit.bili.presentation.theme.generateWearColorSchemeFromSeed
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.BangumiDetailViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.VideoDownloadManager
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import coil.request.SuccessResult

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BangumiDetailScreen(navController: NavHostController, mediaId: Long, viewModel: BangumiDetailViewModel = viewModel()) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (viewModel.bangumiInfo.value == null) {
                    viewModel.loadData(mediaId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val updatedEpid = savedStateHandle?.get<Long>("updatedEpid")
    val updatedProgress = savedStateHandle?.get<Long>("updatedProgress")
    
    LaunchedEffect(updatedEpid, updatedProgress) {
        if (updatedEpid != null && updatedProgress != null) {
            viewModel.updateProgress(updatedEpid, updatedProgress)
            savedStateHandle.remove<Long>("updatedEpid")
            savedStateHandle.remove<Long>("updatedProgress")
        }
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
    
    var showCacheDialog by remember { mutableStateOf(false) }
    var cacheQualities by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var cacheAudioQualities by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var showAudioQualitiesInCacheDialog by remember { mutableStateOf(false) }
    var isFetchingQualities by remember { mutableStateOf(false) }
    var showEpisodeDialogForCache by remember { mutableStateOf(false) }
    var targetEpisodeForCache by remember { mutableStateOf<Bangumi.Episode?>(null) }
    
    var dynamicColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    var isColorExtracted by remember { mutableStateOf(false) }
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
            if (result is SuccessResult) {
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
        if (bangumiInfo != null) {
            isColorExtracted = true
        }
    }
    
    MaterialTheme(colorScheme = dynamicColorScheme ?: defaultColorScheme) {
    Dialog(
        visible = showEpisodeDialogForCache,
        onDismissRequest = { showEpisodeDialogForCache = false }
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val isRound = LocalScreenRound.current

        TransformingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 24.dp),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                ) {
                    Text("选择要缓存的分集", color = MaterialTheme.colorScheme.primary)
                }
            }
            val episodes = bangumiInfo?.sectionList?.flatMap { it.episodes } ?: emptyList()
            items(episodes.size) { index ->
                val ep = episodes[index]
                val name = ep.title_long.ifEmpty { ep.title }
                Button(
                    onClick = {
                        targetEpisodeForCache = ep
                        showEpisodeDialogForCache = false
                        showCacheDialog = true
                        
                        isFetchingQualities = true
                        viewModel.viewModelScope.launch {
                            val info = bangumiInfo?.info
                            if (info != null) {
                                val pd = PlayerApi.getBangumi(PlayerData(
                                    aid = ep.aid, 
                                    cid = ep.cid, 
                                    qn = 112, 
                                    type = PlayerData.TYPE_BANGUMI, 
                                    epid = ep.id, 
                                    sid = info.season_id
                                ))
                                val names = pd.qnStrList
                                val vals = pd.qnValueList
                                if (names != null && vals != null && names.size == vals.size) {
                                    cacheQualities = names.zip(vals.toTypedArray())
                                }
                                val audioList = mutableListOf<Pair<String, Int>>()
                                pd.dashData?.let { dash ->
                                    dash.audioStreams.forEach {
                                        val aname = when(it.id) {
                                            30216 -> "64K"
                                            30232 -> "132K"
                                            30280 -> "192K"
                                            else -> "${it.id}"
                                        }
                                        audioList.add(aname to it.id)
                                    }
                                    if (dash.dolbyAudio != null) audioList.add("杜比全景声" to 30250)
                                    if (dash.flacAudio != null) audioList.add("Hi-Res无损" to 30251)
                                }
                                cacheAudioQualities = audioList
                            }
                            isFetchingQualities = false
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    Dialog(
        visible = showCacheDialog,
        onDismissRequest = { 
            showCacheDialog = false
            showAudioQualitiesInCacheDialog = false
        }
    ) {
        if (isFetchingQualities) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            val isRound = LocalScreenRound.current
            TransformingLazyColumn(
                state = listState, 
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), 
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 24.dp), 
                rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    ) {
                        Text(if (showAudioQualitiesInCacheDialog) "选择音质" else "选择清晰度", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (showAudioQualitiesInCacheDialog) {
                    items(cacheAudioQualities.size) { idx ->
                        val (name, qn) = cacheAudioQualities[idx]
                        Button(
                            onClick = {
                                val info = bangumiInfo?.info
                                if (info != null && targetEpisodeForCache != null) {
                                    val safeTitle = "${info.title}_${targetEpisodeForCache?.title_long?.ifEmpty { targetEpisodeForCache?.title }}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    VideoDownloadManager.enqueue(
                                        url = "",
                                        title = "${info.title} - ${targetEpisodeForCache?.title_long?.ifEmpty { targetEpisodeForCache?.title }}",
                                        filename = "${safeTitle}_audio.m4s",
                                        context = context,
                                        aid = targetEpisodeForCache?.aid ?: 0L,
                                        cid = targetEpisodeForCache?.cid ?: 0L,
                                        bvid = "",
                                        qn = qn,
                                        type = "AUDIO_AND_SUBTITLE",
                                        mediaType = PlayerData.TYPE_BANGUMI,
                                        coverUrl = targetEpisodeForCache?.cover?.ifEmpty { info.cover }?.replace("http://", "https://") ?: "",
                                        duration = 0
                                    )
                                    RoundToast.show(context, "正在下载音频...")
                                    showAudioQualitiesInCacheDialog = false
                                    showCacheDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(name)
                        }
                    }
                    item {
                        Button(
                            onClick = { showAudioQualitiesInCacheDialog = false },
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("返回")
                        }
                    }
                } else {
                    items(cacheQualities.size) { idx ->
                        val (name, qn) = cacheQualities[idx]
                        Button(
                            onClick = {
                                val info = bangumiInfo?.info
                                if (info != null && targetEpisodeForCache != null) {
                                    val safeTitle = "${info.title}_${targetEpisodeForCache?.title_long?.ifEmpty { targetEpisodeForCache?.title }}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                    VideoDownloadManager.enqueue(
                                        url = "",
                                        title = "${info.title} - ${targetEpisodeForCache?.title_long?.ifEmpty { targetEpisodeForCache?.title }}",
                                        filename = "${safeTitle}_$qn.mp4",
                                        context = context,
                                        aid = targetEpisodeForCache?.aid ?: 0L,
                                        cid = targetEpisodeForCache?.cid ?: 0L,
                                        bvid = "",
                                        qn = qn,
                                        type = "MP4",
                                        mediaType = PlayerData.TYPE_BANGUMI,
                                        coverUrl = targetEpisodeForCache?.cover?.ifEmpty { info.cover }?.replace("http://", "https://") ?: "",
                                        duration = 0
                                    )
                                    RoundToast.show(context, "正在下载视频...")
                                    showCacheDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(name)
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                showAudioQualitiesInCacheDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text("仅音频+字幕")
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AnimatedContent(
                targetState = (isLoading || bangumiInfo == null || !isColorExtracted) && errorMessage == null,
                transitionSpec = { fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500)) },
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-15).dp)
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
                                onEpisodeClick = { ep, initialProgress ->
                                    val info = bangumiInfo?.info ?: return@BangumiInfoPage
                                    val qn = SharedPreferencesUtil.getInt("play_qn", 16)
                                    val allEpisodes = bangumiInfo?.sectionList?.flatMap { it.episodes } ?: listOf(ep)
                                    val selectedIndex = allEpisodes.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
                                    
                                    val epLongTitle = ep.title_long
                                    val epTitle = ep.title
                                    val playerData = PlayerData(
                                        title = epLongTitle.ifEmpty { epTitle },
                                        aid = ep.aid,
                                        cid = ep.cid,
                                        cids = allEpisodes.map { it.cid },
                                        epids = allEpisodes.map { it.id },
                                        aids = allEpisodes.map { it.aid },
                                        pagenames = allEpisodes.map { it.title_long.ifEmpty { it.title } },
                                        currentPageIndex = selectedIndex,
                                        type = PlayerData.TYPE_BANGUMI,
                                        epid = ep.id,
                                        sid = info.season_id,
                                        qn = qn,
                                        progress = initialProgress,
                                        bvid = "",
                                        cover = ep.cover.ifEmpty { info.cover }
                                    )
                                    PlayerApi.jumpToPlayer(context, navController, playerData)
                                },
                                onFollowClick = { viewModel.toggleFollow() },
                                onCacheClick = { showEpisodeDialogForCache = true }
                            )
                            1 -> VideoCommentsPage(
                                replies = replies, 
                                replyCount = replyCount,
                                replyErrorMessage = replyErrorMessage,
                                focusRequester = focusRequesters[1],
                                navController = navController,
                                onLoadMore = { viewModel.loadReplies() },
                                onClick = { reply ->
                                    navController.currentBackStackEntry?.savedStateHandle?.set("reply", reply)
                                    navController.navigate("reply_detail")
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
    
    }
    
    WriteReplyScreen(
        visible = showWriteReply,
        targetName = replyTarget?.sender?.name,
        emotes = emotes,
        onSend = { text, _ ->
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

@Composable
fun BangumiInfoPage(
    bangumi: Bangumi?, 
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onEpisodeClick: (Bangumi.Episode, Int) -> Unit,
    onFollowClick: () -> Unit,
    onCacheClick: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
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
                            .adaptiveTransformedHeight(this, transformationSpec)
                            .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                            .fillMaxWidth(0.5f)
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                
                item {
                    Text(
                        text = bangumi.info.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .adaptiveTransformedHeight(this, transformationSpec)
                            .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                            .fillMaxWidth()
                    )
                }

                item {
                    val pubTime = bangumi.info.publish?.pub_time?.take(4)?.plus("年") ?: ""
                    Text(
                        text = "${bangumi.info.area_name} · $pubTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .adaptiveTransformedHeight(this, transformationSpec)
                            .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                val evaluateText = bangumi.info.evaluate
                val progress = bangumi.info.user_status?.progress
                
                if (progress != null && progress.last_ep_id > 0L) {
                    item {
                        Button(
                            onClick = { 
                                val ep = bangumi.sectionList.flatMap { it.episodes }.find { it.id == progress.last_ep_id }
                                if (ep != null) {
                                    onEpisodeClick(ep, progress.last_time.toInt())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("继续观看", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                
                item {
                    val isFollowed = (bangumi.info.user_status?.follow ?: 0) > 0
                    Button(
                        onClick = onFollowClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        icon = { 
                            Icon(
                                if (isFollowed) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                contentDescription = null,
                                tint = if (isFollowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ) 
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (isFollowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(if (isFollowed) "已追番" else "追番", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                item {
                    Button(
                        onClick = onCacheClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        icon = { Icon(Icons.Default.Download, contentDescription = "Cache", tint = MaterialTheme.colorScheme.onSurface) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("缓存", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                item { Spacer(modifier = Modifier.height(1.dp)) }

                if (evaluateText.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .adaptiveTransformedHeight(this, transformationSpec)
                                .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
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
                            modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                        ) {
                            Text("选集")
                        }
                    }
                    
                    val episodes = bangumi.sectionList.firstOrNull()?.episodes ?: emptyList()
                    var lastEpIndex = -1
                    if (progress != null && progress.last_ep_id > 0L) {
                        lastEpIndex = episodes.indexOfFirst { it.id == progress.last_ep_id }
                    }

                    items(episodes.size) { i ->
                        val ep = episodes[i]
                        val isLastWatched = ep.id == progress?.last_ep_id
                        val epProgress = if (isLastWatched) progress.last_time.toInt() else 0
                        val isWatched = lastEpIndex != -1 && i < lastEpIndex
                        val hasDistinctLongTitle = ep.title_long.isNotEmpty() && ep.title_long != ep.title
                        Button(
                            onClick = { onEpisodeClick(ep, epProgress) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer, 
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            icon = if (hasDistinctLongTitle) {
                                {
                                    Text(
                                        text = ep.title,
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
                                    val longTitle = ep.title_long
                                    Text(
                                        text = longTitle.takeIf { it.isNotEmpty() } ?: ep.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 2000
                                        )
                                    )
                                }
                            },
                            secondaryLabel = if (isLastWatched || isWatched) {
                                {
                                    if (isLastWatched && progress != null) {
                                        val timeStr = if (progress.last_time > 0) {
                                            val minutes = progress.last_time / 60
                                            val seconds = progress.last_time % 60
                                            "看到 %02d:%02d".format(minutes, seconds)
                                        } else "刚刚开始"
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (isWatched) {
                                        Text(
                                            text = "已看完",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
        }
    }
}
