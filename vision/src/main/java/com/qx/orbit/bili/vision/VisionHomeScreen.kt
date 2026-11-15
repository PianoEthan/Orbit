package com.qx.orbit.bili.vision

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qx.orbit.bili.core.api.BilibiliAuthGateway
import com.qx.orbit.bili.core.api.BilibiliSessionGateway
import com.qx.orbit.bili.core.api.parseCookieValue
import kotlinx.coroutines.CancellationException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil.request.ImageRequest
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.qx.orbit.bili.core.api.CoreFeed
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
fun VisionHomeScreen(
    viewModel: VisionHomeViewModel,
    onVideoClick: (CoreVideo) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUserCenter: () -> Unit,
    onOpenSeasons: () -> Unit = {},
) {
    val feed by viewModel.feed.collectAsState()
    val revision by viewModel.feedRevision.collectAsState()
    val cookie = VisionUserSession.cookie
    val authGateway = remember { BilibiliAuthGateway() }
    val sessionGateway = remember { BilibiliSessionGateway(cookieProvider = { VisionUserSession.cookie }, cookieSaver = { VisionUserSession.updateCookie(it) }) }
    LaunchedEffect(cookie) {
        viewModel.onSessionChanged(cookie)
        if (parseCookieValue(cookie, "buvid3").isBlank()) {
            try {
                sessionGateway.initializeSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
        if (cookie.isNotBlank() && VisionUserSession.profile == null) {
            try {
                val profile = authGateway.getMyProfile(VisionUserSession.cookie)
                if (cookie == VisionUserSession.cookie) VisionUserSession.updateProfile(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the account entry available when the profile request is offline.
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val rail = maxWidth >= 600.dp || maxHeight < 480.dp
            Row(Modifier.fillMaxSize()) {
                if (rail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Spacer(Modifier.height(12.dp))
                        NavigationRailItem(true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
                        NavigationRailItem(false, onClick = onOpenSearch, icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
                        Spacer(Modifier.weight(1f))
                        VisionAccountButton(onOpenUserCenter)
                        IconButton(onClick = onOpenSettings, modifier = Modifier.padding(bottom = 16.dp)) { Icon(Icons.Default.Settings, "打开设置") }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryTabRow(
                            selectedTabIndex = if (feed == CoreFeed.RECOMMEND) 0 else 1,
                            modifier = Modifier.weight(1f).widthIn(max = 400.dp).focusGroup(), divider = {},
                        ) {
                            listOf(CoreFeed.RECOMMEND to "推荐", CoreFeed.POPULAR to "热门").forEach { (source, title) ->
                                Tab(feed == source, onClick = { viewModel.selectFeed(source) }, text = { Text(title, style = MaterialTheme.typography.titleMedium) })
                            }
                        }
                        TextButton(onClick = onOpenSeasons, modifier = Modifier.heightIn(min = 48.dp)) { Text("番剧/影视") }
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "刷新内容") }
                    }
                    VisionVideoFeed(viewModel, onVideoClick, Modifier.weight(1f), source = "${feed.name}:$revision")
                    if (!rail) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer), verticalAlignment = Alignment.CenterVertically) {
                            NavigationBar(Modifier.weight(1f)) {
                                NavigationBarItem(true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
                                NavigationBarItem(false, onClick = onOpenSearch, icon = { Icon(Icons.Default.Search, null) }, label = { Text("搜索") })
                            }
                            VisionAccountButton(onOpenUserCenter)
                            IconButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 12.dp)) { Icon(Icons.Default.Settings, "打开设置") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisionVideoFeed(viewModel: VisionHomeViewModel, onVideoClick: (CoreVideo) -> Unit, modifier: Modifier = Modifier, source: String) {
    val videos by viewModel.videos.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val inputMode = LocalInputModeManager.current
    val gridState = rememberLazyGridState()
    var returnVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreFocus by rememberSaveable { mutableStateOf(false) }
    var previousSource by rememberSaveable { mutableStateOf(source) }
    LaunchedEffect(source) {
        if (source != previousSource) {
            gridState.scrollToItem(0)
            returnVideo = null
            restoreFocus = false
            previousSource = source
        }
    }
    if (videos.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            FeedStatus(loading, error, false, true, viewModel::refresh)
        }
    } else {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(visionGridColumns(maxWidth.value)), state = gridState,
                contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp),
                modifier = Modifier.fillMaxSize().onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.PageDown) { viewModel.loadMore(); true } else false
                },
            ) {
                items(videos, key = { if (it.bvid.isNotBlank()) it.bvid else "video_${it.id}" }, contentType = { "video" }) { video ->
                    val requester = remember { FocusRequester() }
                    LaunchedEffect(restoreFocus, returnVideo) {
                        if (restoreFocus && returnVideo == video.bvid) {
                            if (inputMode.inputMode == InputMode.Keyboard) { withFrameNanos { }; requester.requestFocus() }
                            restoreFocus = false
                        }
                    }
                    VisionExpressiveVideoCard(video, Modifier.focusRequester(requester)) {
                        returnVideo = video.bvid
                        restoreFocus = true
                        onVideoClick(video)
                    }
                }
                item(key = "footer_status", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        FeedStatus(loading, error, hasMore, false, viewModel::loadMore)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedStatus(loading: Boolean, error: String?, hasMore: Boolean, empty: Boolean, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            loading -> { LoadingIndicator(); Text("正在加载视频", style = MaterialTheme.typography.bodyMedium) }
            error != null -> { Text(error, color = MaterialTheme.colorScheme.error); FilledTonalButton(onClick = onRetry) { Text("重新加载") } }
            empty -> { Text("暂无视频", color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = onRetry) { Text("刷新") } }
            hasMore -> FilledTonalButton(onClick = onRetry) { Text("加载更多") }
            else -> Text("已加载全部内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VisionExpressiveVideoCard(video: CoreVideo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = animateFloatAsState(if (focused) 1.035f else 1f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "cardScale")
    val radius = animateDpAsState(if (focused) 28.dp else 12.dp, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "cardRadius")
    val color = animateColorAsState(if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "cardColor")
    val shape = remember {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val corner = with(density) { radius.value.toPx() }.coerceAtMost(size.minDimension / 2)
                return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
            }
        }
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val imageWidth = with(density) { (widthDp / visionGridColumns(widthDp.toFloat())).dp.roundToPx() }.coerceIn(160, 1280)
    val request = remember(video.cover, imageWidth, context) {
        ImageRequest.Builder(context).data(visionThumbnailUrl(video.cover, imageWidth))
            .size(imageWidth, imageWidth * 9 / 16).crossfade(false)
            .setHeader("Referer", "https://www.bilibili.com/").build()
    }
    val scrim = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))) }
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth().zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .drawBehind { drawRoundRect(color.value, cornerRadius = CornerRadius(radius.value.toPx())) },
        interactionSource = interactionSource, shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(scrim))
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(video.viewText, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1f))
                Text(video.durationText, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium)
            Text(video.author, style = MaterialTheme.typography.bodySmall, color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
