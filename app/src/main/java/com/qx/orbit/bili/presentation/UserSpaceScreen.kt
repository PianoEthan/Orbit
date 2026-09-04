package com.qx.orbit.bili.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.qx.orbit.bili.presentation.theme.ActiveDynamicTheme
import com.qx.orbit.bili.presentation.theme.extractSeedColorFromBitmap
import com.qx.orbit.bili.presentation.theme.generateWearColorSchemeFromSeed
import androidx.compose.runtime.setValue
import com.qx.orbit.bili.presentation.ui.components.LevelIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.data.model.Dynamic
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.presentation.ui.components.DynamicCard
import com.qx.orbit.bili.data.model.ArticleCard
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.viewmodel.UserSpaceViewModel
import com.qx.orbit.bili.util.formatCount
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.data.remote.CookieManager
import coil.request.SuccessResult

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserSpaceScreen(
    mid: Long,
    viewModel: UserSpaceViewModel,
    navController: NavHostController
) {
    LaunchedEffect(mid) {
        viewModel.initUser(mid)
    }

    val userInfo by viewModel.userInfo.collectAsState()
    val dynamics by viewModel.dynamics.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val articles by viewModel.articles.collectAsState()
    val isFollowed by viewModel.isFollowed.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 3 })
    val focusRequesters = remember { List(3) { FocusRequester() } }
    val currentMid = remember { CookieManager.getMid() }
    val isSelf = mid == currentMid

    val context = LocalContext.current
    var dynamicColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    var isColorExtracted by remember { mutableStateOf(false) }
    val defaultColorScheme = MaterialTheme.colorScheme

    LaunchedEffect(userInfo) {
        val rawCover = userInfo?.avatar ?: ""
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
        if (userInfo != null) {
            isColorExtracted = true
        }
    }

    MaterialTheme(colorScheme = dynamicColorScheme ?: defaultColorScheme) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = userInfo == null || !isColorExtracted,
                transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
                label = "LoadingAnimation"
            ) { isInitialLoading ->
                if (isInitialLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val isFocusedPage = pagerState.currentPage == page
                            LaunchedEffect(isFocusedPage) {
                                if (isFocusedPage) {
                                    try { focusRequesters[page].requestFocus() } catch (e: Exception) {}
                                }
                            }
    
                            when (page) {
                                0 -> UserDynamicsPage(userInfo, dynamics, focusRequesters[0], navController, viewModel, isFollowed, isSelf)
                                1 -> UserVideosPage(userInfo, videos, focusRequesters[1], navController, viewModel)
                                2 -> UserArticlesPage(userInfo, articles, focusRequesters[2], navController, viewModel)
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
}

@Composable
fun UserDynamicsPage(
    userInfo: UserInfo?,
    dynamics: List<Dynamic>,
    focusRequester: FocusRequester,
    navController: NavHostController,
    viewModel: UserSpaceViewModel,
    isFollowed: Boolean = false,
    isSelf: Boolean = false
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    val signExpanded = remember { mutableStateOf(false) }
    
    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState, 
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            UserSpaceBackground(listState, userInfo?.avatar)
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                userInfo?.let { info ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .adaptiveTransformedHeight(this, transformationSpec)
                            .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isLive = info.live_room?.live_status == 1
                        val avatarModifier = Modifier
                            .size(60.dp)
                            .let {
                                if (isLive) {
                                    it.clickable {
                                        navController.navigate("live_room/${info.live_room.roomid}")
                                    }
                                } else {
                                    it
                                }
                            }

                        UserAvatar(
                            avatarUrl = info.avatar,
                            officialRole = info.official,
                            modifier = avatarModifier,
                            isVip = info.vip_role > 0,
                            isLive = isLive
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserNameText(
                                name = info.name,
                                isVip = info.vip_role > 0,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            LevelIcon(level = info.level, isSenior = info.is_senior_member == 1)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${formatCount(info.fans)}粉丝  ${info.following}关注", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.sign,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = if (signExpanded.value) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { signExpanded.value = !signExpanded.value }
                        )
                        if (!isSelf) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.toggleFollow() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (isFollowed) "已关注" else "关注",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(dynamics) { index, item ->
                if (index >= dynamics.size - 3) {
                    LaunchedEffect(index) {
                        viewModel.loadMoreDynamics()
                    }
                }
                Box(modifier = Modifier.adaptiveTransformedHeight(this@itemsIndexed, transformationSpec)) {
                    DynamicCard(
                        item = item,
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        onClick = { if (item.major_type == "MAJOR_TYPE_OPUS" || item.major_type == "MAJOR_TYPE_ARTICLE") navController.navigate("opus_detail/${item.dynamicId}") else navController.navigate("dynamic_detail/${item.dynamicId}") },
                        onUserClick = { mid -> navController.navigate("user_space/$mid") },
                        onArchiveClick = { bvid, aid -> navController.navigate("detail/$bvid/$aid") },
                        onLiveClick = { roomId -> navController.navigate("live_room/$roomId") },
                        onRemove = viewModel::removeDynamicLocally
                    )
                }
            }
        }
        }
    }
}

@Composable
fun UserVideosPage(
    userInfo: UserInfo?,
    videos: List<VideoCard>,
    focusRequester: FocusRequester,
    navController: NavHostController,
    viewModel: UserSpaceViewModel
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState, 
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                ) {
                    Text("发布的视频", color = MaterialTheme.colorScheme.primary)
                }
            }
            itemsIndexed(videos) { index, item ->
                if (index >= videos.size - 3) {
                    LaunchedEffect(index) {
                        viewModel.loadMoreVideos()
                    }
                }
                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).adaptiveTransformedHeight(this@itemsIndexed, transformationSpec)) {
                    RecommendVideoCard(
                        item = item, 
                        onClick = {
                            navController.navigate("detail/${item.bvid}/${item.aid}")
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    )
                }
            }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun UserArticlesPage(
    userInfo: UserInfo?,
    articles: List<ArticleCard>,
    focusRequester: FocusRequester,
    navController: NavHostController,
    viewModel: UserSpaceViewModel
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState, 
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                ) {
                    Text("发布的图文", color = MaterialTheme.colorScheme.primary)
                }
            }
            itemsIndexed(articles) { index, item ->
                if (index >= articles.size - 3) {
                    LaunchedEffect(index) {
                        viewModel.loadMoreArticles()
                    }
                }
                Box(modifier = Modifier.adaptiveTransformedHeight(this@itemsIndexed, transformationSpec)) {
                    ArticleCardItem(
                        item = item, 
                        onClick = {
                            navController.navigate("article_detail/${item.id}")
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    )
                }
            }
                item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

@Composable
fun UserSpaceBackground(
    listState: androidx.wear.compose.foundation.lazy.TransformingLazyColumnState,
    avatarUrl: String?
) {
    val secureCover = avatarUrl?.replace("http://", "https://") ?: ""
    if (secureCover.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(secureCover)
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
                val headerItem = listState.layoutInfo.visibleItems.find { it.index == 0 }
                alpha = if (headerItem != null) {
                    val scrolled = -headerItem.offset.toFloat()
                    (scrolled / 200f).coerceIn(0f, 1f)
                } else {
                    1f
                }
            }
            .background(Color.Black)
    )
}
