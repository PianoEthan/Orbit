package com.qx.orbit.bili.vision

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Timeline
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.qx.orbit.bili.core.api.BilibiliPlaybackGateway
import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.PlaybackGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@AndroidOptIn(UnstableApi::class)
@Composable
internal fun VisionPlayerScreen(
    video: CoreVideo,
    onBack: () -> Unit,
    historyModel: VisionPlaybackHistoryViewModel = viewModel(),
    gateway: PlaybackGateway = remember { BilibiliPlaybackGateway(cookieProvider = { VisionUserSession.cookie }) },
    episodesModel: VisionPlayerEpisodesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inputMode = LocalInputModeManager.current
    val episodes by episodesModel.state.collectAsState()
    LaunchedEffect(video.bvid) { episodesModel.open(video) }
    var selectedCid by rememberSaveable(video.bvid, video.cid) { mutableLongStateOf(video.cid) }
    var activeCid by rememberSaveable(video.bvid, video.cid) { mutableLongStateOf(video.cid) }
    val targetVideo = video.copy(cid = selectedCid)
    var requestedQuality by rememberSaveable(video.bvid) { mutableIntStateOf(32) }
    var choosingQuality by remember { mutableStateOf(false) }
    var choosingSpeed by remember { mutableStateOf(false) }
    var choosingEpisode by remember { mutableStateOf(false) }
    val episodeFocus = remember { FocusRequester() }
    var restoreEpisodeFocus by remember { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable(video.bvid) { mutableFloatStateOf(1f) }
    val speedFocus = remember { FocusRequester() }
    var restoreSpeedFocus by remember { mutableStateOf(false) }
    var qualityMessage by remember { mutableStateOf<String?>(null) }
    var resumeSnapshot by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var playback by remember(video.bvid) { mutableStateOf<CorePlayback?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var resumePosition by rememberSaveable(video.bvid) { mutableLongStateOf(video.startPositionMs.coerceAtLeast(0)) }
    var resumeSegment by rememberSaveable(video.bvid) { mutableIntStateOf(0) }
    var wantsPlayback by rememberSaveable(video.bvid) { mutableStateOf(true) }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var consumedKey by remember { mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val source = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com"))
    }
    val player = remember(context, video.bvid) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
    }
    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(supportedPlaybackSpeed(playbackSpeed))
    }
    val canControl = playback != null && error == null && !loading
    val historySyncFailed by historyModel.syncFailed.collectAsState()
    fun reportHistory(force: Boolean = false) {
        historyModel.setAccount(VisionUserSession.cookie)
        val current = playback ?: return
        if (loading || player.mediaItemCount == 0) return
        val segment = player.currentMediaItemIndex.coerceAtLeast(0)
        val timeline = player.currentTimeline
        val durations = (0 until segment).map { index ->
            if (index < timeline.windowCount) timeline.getWindow(index, Timeline.Window()).durationMs else -1L
        }
        val seconds = historyPositionSeconds(segment, player.currentPosition, durations) ?: return
        historyModel.sample(VisionHistoryProgress(current.video.id, current.video.cid, seconds), player.isPlaying, force)
    }
    fun markInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        controlsVisible = true
    }
    fun togglePlayback() {
        markInteraction()
        if (!canControl) return
        wantsPlayback = !wantsPlayback
        player.playWhenReady = playbackShouldRun(wantsPlayback, foreground, canControl)
    }
    fun seekBy(deltaMs: Long) {
        markInteraction()
        if (canControl) player.seekTo(seekPlaybackPosition(player.currentPosition, deltaMs, durationMs))
    }
    BackHandler {
        if (controlsVisible && error == null) controlsVisible = false else onBack()
    }
    LaunchedEffect(video.bvid, selectedCid, retryTrigger, requestedQuality) {
        val previous = playback
        val switchingEpisode = selectedCid != activeCid
        val snapshot = playbackSwitchPosition(activeCid, selectedCid, resumeSnapshot ?: (resumeSegment to resumePosition))
        reportHistory(force = true)
        loading = true
        error = null
        player.pause()
        try {
            val resolved = resolveVisionPlayback(gateway, targetVideo, requestedQuality)
            val sources = buildVisionMediaSources(resolved, source)
            val seek = if (previous == null && snapshot.first == 0) playbackSegmentPosition(snapshot.second, resolved.segmentDurationsMs) else snapshot
            player.setMediaSources(sources)
            player.seekTo(seek.first.coerceIn(resolved.urls.indices), seek.second)
            playback = resolved
            activeCid = resolved.video.cid
            resumePosition = seek.second
            resumeSegment = seek.first
            resumeSnapshot = null
            scrubFraction = null
            qualityMessage = if (resolved.quality != requestedQuality) "请求画质未获授权或不可用，当前实际画质：${resolved.qualities.firstOrNull { it.id == resolved.quality }?.label ?: resolved.quality}" else null
            player.prepare()
            player.playWhenReady = playbackShouldRun(wantsPlayback, foreground, true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            coroutineContext.ensureActive()
            if (previous != null && player.playbackState != Player.STATE_IDLE) {
                qualityMessage = if (switchingEpisode) "选集切换失败，继续保留原集；可打开选集重试" else "画质切换失败，继续播放原画质"
                resumeSnapshot = null
                player.playWhenReady = playbackShouldRun(wantsPlayback, foreground, true)
            } else error = exception.message ?: "无法载入视频，请检查网络后重试"
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }
    val latestCanControl by rememberUpdatedState(canControl)
    val latestReportHistory by rememberUpdatedState<(Boolean) -> Unit>({ reportHistory(it) })
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                latestReportHistory(!playing)
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    wantsPlayback = false
                    controlsVisible = true
                }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onPlayerError(exception: PlaybackException) {
                error = "播放遇到错误 (${exception.errorCodeName})，当前流的可用地址已尝试"
                controlsVisible = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    latestReportHistory(true)
                    foreground = false
                    resumePosition = player.currentPosition.coerceAtLeast(0)
                    resumeSegment = player.currentMediaItemIndex.coerceAtLeast(0)
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    player.playWhenReady = playbackShouldRun(wantsPlayback, true, latestCanControl)
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            latestReportHistory(true)
            historyModel.endPlayback()
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            if (player.mediaItemCount > 0) {
                resumePosition = positionMs
                resumeSegment = player.currentMediaItemIndex
            }
            if (player.isPlaying) latestReportHistory(false)
            delay(500)
        }
    }
    val controlsTimeout = VisionPreferences.controlsTimeoutMs
    LaunchedEffect(controlsVisible, isPlaying, isBuffering, error, lastInteractionTime, scrubFraction, controlsTimeout, choosingQuality, choosingSpeed, choosingEpisode) {
        if (controlsVisible && isPlaying && !isBuffering && error == null && scrubFraction == null && controlsTimeout > 0 && !choosingQuality && !choosingSpeed && !choosingEpisode) {
            delay(controlsTimeout)
            controlsVisible = false
        }
    }
    LaunchedEffect(canControl, error, controlsVisible, inputMode.inputMode, choosingSpeed, choosingQuality, choosingEpisode) {
        if (!choosingSpeed && !choosingQuality && !choosingEpisode && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            when {
                error != null -> retryFocus.requestFocus()
                !controlsVisible -> rootFocus.requestFocus()
                restoreEpisodeFocus -> { episodeFocus.requestFocus(); restoreEpisodeFocus = false }
                restoreSpeedFocus && canControl -> { speedFocus.requestFocus(); restoreSpeedFocus = false }
                canControl -> playFocus.requestFocus()
                else -> backFocus.requestFocus()
            }
        }
    }
    if (choosingEpisode) {
        VisionPlayerEpisodePicker(episodes, activeCid,
            onSelect = { cid ->
                choosingEpisode = false
                markInteraction()
                if (cid != activeCid) {
                    if (selectedCid == cid) retryTrigger++ else selectedCid = cid
                }
            }, onRetry = episodesModel::retry,
            onDismiss = { choosingEpisode = false; markInteraction() })
    }
    if (choosingQuality) {
        AlertDialog(
            onDismissRequest = { choosingQuality = false; markInteraction() },
            title = { Text("选择清晰度") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${if (playback?.audioUrl != null) "当前使用 DASH 音视频分离流" else "当前使用 MP4 播放流"}；需要登录或大会员的画质以服务器授权为准。", style = MaterialTheme.typography.bodySmall)
                    playback?.qualities.orEmpty().forEach { quality ->
                        TextButton(onClick = {
                            choosingQuality = false
                            markInteraction()
                            if (quality.id != playback?.quality) {
                                selectedCid = activeCid
                                resumeSnapshot = player.currentMediaItemIndex.coerceAtLeast(0) to player.currentPosition.coerceAtLeast(0)
                                if (requestedQuality == quality.id) retryTrigger++ else requestedQuality = quality.id
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${if (quality.id == playback?.quality) "✓ " else ""}${quality.label}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingQuality = false; markInteraction() }) { Text("关闭") } },
        )
    }
    if (choosingSpeed) {
        AlertDialog(
            onDismissRequest = { choosingSpeed = false; markInteraction() },
            title = { Text("播放速度") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    visionPlaybackSpeeds.forEach { speed ->
                        TextButton(onClick = { playbackSpeed = speed; choosingSpeed = false; markInteraction() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${if (speed == supportedPlaybackSpeed(playbackSpeed)) "✓ " else ""}${speed}×${if (speed == 1f) " 正常速度" else ""}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSpeed = false; markInteraction() }) { Text("关闭") } },
        )
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_UP && native.keyCode == consumedKey) {
                    consumedKey = KeyEvent.KEYCODE_UNKNOWN
                    return@onPreviewKeyEvent true
                }
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val key = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> PlaybackKey.CONFIRM
                    KeyEvent.KEYCODE_DPAD_LEFT -> PlaybackKey.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> PlaybackKey.RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> PlaybackKey.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> PlaybackKey.DOWN
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_SPACE -> PlaybackKey.PLAY_PAUSE
                    KeyEvent.KEYCODE_MEDIA_PLAY -> PlaybackKey.PLAY
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackKey.PAUSE
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlaybackKey.FAST_FORWARD
                    KeyEvent.KEYCODE_MEDIA_REWIND -> PlaybackKey.REWIND
                    else -> PlaybackKey.OTHER
                }
                val action = playbackKeyAction(key, controlsVisible || error != null)
                if (action == PlaybackKeyAction.PASS) {
                    if (key != PlaybackKey.OTHER) lastInteractionTime = System.currentTimeMillis()
                    false
                } else {
                    consumedKey = native.keyCode
                    when (action) {
                        PlaybackKeyAction.SHOW_CONTROLS -> markInteraction()
                        PlaybackKeyAction.TOGGLE -> togglePlayback()
                        PlaybackKeyAction.PLAY, PlaybackKeyAction.PAUSE -> {
                            markInteraction()
                            if (canControl) {
                                wantsPlayback = action == PlaybackKeyAction.PLAY
                                player.playWhenReady = playbackShouldRun(wantsPlayback, foreground, canControl)
                            }
                        }
                        PlaybackKeyAction.SEEK_FORWARD -> seekBy(10_000)
                        PlaybackKeyAction.SEEK_BACKWARD -> seekBy(-10_000)
                        PlaybackKeyAction.PASS -> Unit
                    }
                    true
                }
            }
            .focusRequester(rootFocus)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (controlsVisible && error == null) controlsVisible = false else markInteraction()
            },
        contentAlignment = Alignment.Center,
    ) {
        val viewport = fitVideoViewport(maxWidth.value, maxHeight.value, videoAspectRatio)
        val compact = maxWidth < 600.dp
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).apply { player.setVideoSurfaceView(this) } },
            modifier = Modifier.size(viewport.width.dp, viewport.height.dp),
        )
        if (error == null && (loading || isBuffering)) {
            CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
        }
        error?.let { message ->
            Surface(Modifier.padding(24.dp).widthIn(max = 520.dp).zIndex(5f), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("播放异常", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        markInteraction()
                        resumePosition = player.currentPosition.coerceAtLeast(0)
                        resumeSegment = player.currentMediaItemIndex.coerceAtLeast(0)
                        retryTrigger++
                    }, modifier = Modifier.focusRequester(retryFocus)) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重试")
                    }
                    TextButton(onClick = onBack) { Text("返回详情") }
                }
            }
        }
        AnimatedVisibility(controlsVisible && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize().zIndex(3f)) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(onBack, { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }, Modifier.focusRequester(backFocus))
                    Spacer(Modifier.width(12.dp))
                    Text(video.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { markInteraction(); choosingQuality = true }, enabled = canControl) {
                        Text(playback?.let { current -> current.qualities.firstOrNull { it.id == current.quality }?.label } ?: "清晰度")
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    qualityMessage?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
                    if (historySyncFailed) Text("观看进度暂未同步，网络恢复后继续观看将再次尝试", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    val shownPosition = scrubFraction?.let { (it * durationMs).toLong() } ?: positionMs
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlaybackTime(shownPosition), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        Text(formatPlaybackTime(durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                    val sliderState = remember { SliderState() }
                    sliderState.value = scrubFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Slider(
                        state = sliderState,
                        onValueChange = { markInteraction(); scrubFraction = it },
                        onValueChangeFinished = {
                            scrubFraction?.let { player.seekTo((it * durationMs).toLong()) }
                            scrubFraction = null
                            markInteraction()
                        },
                        enabled = canControl && durationMs > 0,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "播放进度" },
                    )
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerIconButton({ seekBy(-10_000) }, { Icon(Icons.Default.FastRewind, "快退 10 秒", tint = Color.White) }, enabled = canControl)
                        Button(onClick = ::togglePlayback, enabled = canControl, modifier = Modifier.heightIn(min = 56.dp).focusRequester(playFocus)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPlaying) "暂停" else "播放")
                        }
                        PlayerIconButton({ seekBy(10_000) }, { Icon(Icons.Default.FastForward, "快进 10 秒", tint = Color.White) }, enabled = canControl)
                        FilledTonalButton(onClick = { markInteraction(); restoreSpeedFocus = true; choosingSpeed = true }, enabled = canControl,
                            modifier = Modifier.heightIn(min = 56.dp).focusRequester(speedFocus)) {
                            Text("${supportedPlaybackSpeed(playbackSpeed)}×")
                        }
                        FilledTonalButton(onClick = { markInteraction(); restoreEpisodeFocus = true; choosingEpisode = true }, enabled = !loading,
                            modifier = Modifier.heightIn(min = 56.dp).focusRequester(episodeFocus)) {
                            Text(episodes.pages.firstOrNull { it.cid == activeCid }?.let { "选集 P${it.page}" } ?: "选集")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerIconButton(onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val color by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primaryContainer else Color(0xFF303034),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "playerButtonColor",
    )
    val radius by animateDpAsState(
        if (focused) 16.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "playerButtonShape",
    )
    Surface(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        shape = RoundedCornerShape(radius), color = color,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.size(56.dp),
    ) { Box(contentAlignment = Alignment.Center) { icon() } }
}

private fun formatPlaybackTime(timeMs: Long): String {
    val total = timeMs / 1000
    val seconds = (total % 60).toString().padStart(2, '0')
    return if (total >= 3600) "${total / 3600}:${(total / 60 % 60).toString().padStart(2, '0')}:$seconds"
    else "${total / 60}:$seconds"
}
