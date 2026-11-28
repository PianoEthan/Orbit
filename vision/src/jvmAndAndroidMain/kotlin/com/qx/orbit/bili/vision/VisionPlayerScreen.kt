package com.qx.orbit.bili.vision

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qx.orbit.bili.core.api.BilibiliDanmakuGateway
import com.qx.orbit.bili.core.api.DanmakuGateway
import com.qx.orbit.bili.core.api.BilibiliPlaybackGateway
import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.PlaybackGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun VisionPlayerScreen(
    video: CoreVideo,
    onBack: () -> Unit,
    historyModel: VisionPlaybackHistoryViewModel = viewModel { VisionPlaybackHistoryViewModel() },
    gateway: PlaybackGateway = remember { BilibiliPlaybackGateway(cookieProvider = { VisionUserSession.cookie }) },
    episodesModel: VisionPlayerEpisodesViewModel = viewModel { VisionPlayerEpisodesViewModel() },
    danmakuGateway: DanmakuGateway = remember { BilibiliDanmakuGateway(cookieProvider = { VisionUserSession.cookie }) },
    playerFactory: (VisionPlayerEngine) -> VisionPlayerAdapter = visionPlayerFactory(),
    subtitleModel: VisionSubtitleViewModel = viewModel { VisionSubtitleViewModel() },
) {
    val subtitles by subtitleModel.state.collectAsState()
    var choosingSubtitle by remember { mutableStateOf(false) }
    var videoZoomed by rememberSaveable(video.bvid, video.cid) { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inputMode = LocalInputModeManager.current
    val episodes by episodesModel.state.collectAsState()
    LaunchedEffect(video.bvid) { episodesModel.open(video) }
    var selectedCid by rememberSaveable(video.bvid, video.cid) { mutableLongStateOf(video.cid) }
    var activeCid by rememberSaveable(video.bvid, video.cid) { mutableLongStateOf(video.cid) }
    val targetVideo = if (video.seasonId > 0 && selectedCid != video.cid) episodesModel.episodeVideo(selectedCid) ?: video.copy(cid = selectedCid) else video.copy(cid = selectedCid)
    var requestedQuality by rememberSaveable(video.bvid) { mutableIntStateOf(32) }
    var choosingQuality by remember { mutableStateOf(false) }
    var choosingSpeed by remember { mutableStateOf(false) }
    var choosingEpisode by remember { mutableStateOf(false) }
    var sendingDanmaku by remember { mutableStateOf(false) }
    var danmakuProgress by remember { mutableLongStateOf(0L) }
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
    var consumedKey by remember { mutableStateOf<Key?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val engine = platformVisionEngine(VisionPreferences.playerEngine)
    val player = remember(video.bvid, engine, playerFactory) { playerFactory(engine) }
    val danmakuHost = remember(video.bvid, danmakuGateway) { VisionDanmakuHost(danmakuGateway) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playback?.video?.cid) { playback?.video?.cid?.let { danmakuHost.load(it, scope) } }
    LaunchedEffect(playback?.video?.cid, VisionUserSession.cookie) {
        playback?.video?.let { subtitleModel.open(it, VisionUserSession.cookie) }
    }
    LaunchedEffect(VisionPreferences.danmakuEnabled, VisionPreferences.danmakuAlpha, VisionPreferences.danmakuTextScale) {
        danmakuHost.setEnabled(VisionPreferences.danmakuEnabled)
        danmakuHost.updateConfig(VisionPreferences.danmakuAlpha, VisionPreferences.danmakuTextScale)
    }
    LaunchedEffect(isPlaying, isBuffering, foreground) {
        danmakuHost.setPlaying(isPlaying && !isBuffering && foreground)
    }
    DisposableEffect(danmakuHost) { onDispose { danmakuHost.release() } }
    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(supportedPlaybackSpeed(playbackSpeed))
    }
    val canControl = playback != null && error == null && !loading
    LaunchedEffect(player, wantsPlayback, foreground, canControl) {
        if (playbackShouldRun(wantsPlayback, foreground, canControl)) player.play() else player.pause()
    }
    val historySyncFailed by historyModel.syncFailed.collectAsState()
    fun reportHistory(force: Boolean = false) {
        historyModel.setAccount(VisionUserSession.cookie)
        val current = playback ?: return
        if (loading || player.mediaItemCount == 0) return
        val segment = player.currentMediaItemIndex.coerceAtLeast(0)
        val durations = player.segmentDurationsMs.take(segment)
        val seconds = historyPositionSeconds(segment, player.currentPosition, durations) ?: return
        historyModel.sample(VisionHistoryProgress(current.video.id, current.video.cid, seconds, current.video.episodeId, current.video.seasonId), player.isPlaying, force)
    }
    fun markInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        controlsVisible = true
    }
    fun togglePlayback() {
        markInteraction()
        if (!canControl) return
        wantsPlayback = !wantsPlayback
        if (playbackShouldRun(wantsPlayback, foreground, canControl)) player.play() else player.pause()
    }
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        val elapsed = player.segmentDurationsMs.take(player.currentMediaItemIndex).sumOf { it.coerceAtLeast(0) } + positionMs
        danmakuHost.synchronize(elapsed, player.isPlaying && foreground, supportedPlaybackSpeed(playbackSpeed))
    }
    fun seekBy(deltaMs: Long) {
        markInteraction()
        if (canControl) seekTo(seekPlaybackPosition(player.currentPosition, deltaMs, durationMs))
    }
    BackHandler {
        if (controlsVisible && error == null) controlsVisible = false else onBack()
    }
    LaunchedEffect(player, video.bvid, selectedCid, retryTrigger, requestedQuality, VisionUserSession.cookie) {
        val previous = playback
        val switchingEpisode = selectedCid != activeCid
        val snapshot = playbackSwitchPosition(activeCid, selectedCid, resumeSnapshot ?: (resumeSegment to resumePosition))
        reportHistory(force = true)
        loading = true
        error = null
        player.pause()
        try {
            val resolved = resolveVisionPlayback(gateway, targetVideo, requestedQuality, engine == VisionPlayerEngine.IJK)
            val seek = if (previous == null && snapshot.first == 0) playbackSegmentPosition(snapshot.second, resolved.segmentDurationsMs) else snapshot
            player.prepare(resolved, seek.first, seek.second)
            playback = resolved
            activeCid = resolved.video.cid
            resumePosition = seek.second
            resumeSegment = seek.first
            resumeSnapshot = null
            scrubFraction = null
            qualityMessage = if (resolved.quality != requestedQuality) "请求画质未获授权或不可用，当前实际画质：${resolved.qualities.firstOrNull { it.id == resolved.quality }?.label ?: resolved.quality}" else null
            if (playbackShouldRun(wantsPlayback, foreground, true)) player.play() else player.pause()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            coroutineContext.ensureActive()
            if (previous != null && player.mediaItemCount > 0) {
                qualityMessage = if (switchingEpisode) "选集切换失败，继续保留原集；可打开选集重试" else "画质切换失败，继续播放原画质"
                resumeSnapshot = null
                if (playbackShouldRun(wantsPlayback, foreground, true)) player.play() else player.pause()
            } else error = exception.message ?: "无法载入视频，请检查网络后重试"
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }
    val onPlaybackEnded by rememberUpdatedState<() -> Unit>({
        val next = episodes.pages.getOrNull(episodes.pages.indexOfFirst { it.cid == activeCid } + 1)
        when (playbackCompletion(VisionPreferences.loopPlayback, VisionPreferences.autoNextPart, activeCid, episodes.pages.map { it.cid })) {
            PlaybackCompletion.REPEAT -> {
                resumeSnapshot = 0 to 0L
                resumePosition = 0L
                resumeSegment = 0
                wantsPlayback = true
                retryTrigger++
            }
            PlaybackCompletion.NEXT -> next?.let { wantsPlayback = true; selectedCid = it.cid }
            PlaybackCompletion.STOP -> wantsPlayback = false
        }
        controlsVisible = true
    })
    val latestCanControl by rememberUpdatedState(canControl)
    val latestReportHistory by rememberUpdatedState<(Boolean) -> Unit>({ reportHistory(it) })
    DisposableEffect(player, lifecycle) {
        val listener = object : VisionPlayerListener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                latestReportHistory(!playing)
            }
            override fun onPlaybackStateChanged(buffering: Boolean, isEnded: Boolean) {
                isBuffering = buffering
                if (isEnded) onPlaybackEnded()
            }
            override fun onVideoSizeChanged(aspectRatio: Float) {
                videoAspectRatio = aspectRatio
            }
            override fun onError(message: String) {
                error = message
                isBuffering = false
                isPlaying = false
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
                    if (playbackShouldRun(wantsPlayback, true, latestCanControl)) player.play() else player.pause()
                }
                else -> Unit
            }
        }
        player.setListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            latestReportHistory(true)
            historyModel.endPlayback()
            lifecycle.removeObserver(observer)
            player.setListener(null)
            player.release()
        }
    }
    LaunchedEffect(player, danmakuHost, playbackSpeed, foreground) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            if (player.mediaItemCount > 0) {
                resumePosition = positionMs
                resumeSegment = player.currentMediaItemIndex
            }
            val elapsed = player.segmentDurationsMs.take(player.currentMediaItemIndex).sumOf { it.coerceAtLeast(0) } + positionMs
            elapsedMs = elapsed
            danmakuHost.synchronize(elapsed, player.isPlaying && foreground, supportedPlaybackSpeed(playbackSpeed))
            if (player.isPlaying) latestReportHistory(false)
            delay(100)
        }
    }
    val controlsTimeout = VisionPreferences.controlsTimeoutMs
    LaunchedEffect(controlsVisible, isPlaying, isBuffering, error, lastInteractionTime, scrubFraction, controlsTimeout, choosingQuality, choosingSpeed, choosingEpisode, choosingSubtitle, sendingDanmaku) {
        if (controlsVisible && isPlaying && !isBuffering && error == null && scrubFraction == null && controlsTimeout > 0 && !choosingQuality && !choosingSpeed && !choosingEpisode && !choosingSubtitle && !sendingDanmaku) {
            delay(controlsTimeout)
            controlsVisible = false
        }
    }
    LaunchedEffect(canControl, error, controlsVisible, inputMode.inputMode, choosingSpeed, choosingQuality, choosingEpisode, choosingSubtitle, sendingDanmaku) {
        if (!choosingSpeed && !choosingQuality && !choosingEpisode && !choosingSubtitle && !sendingDanmaku && inputMode.inputMode == InputMode.Keyboard) {
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
    if (choosingSubtitle) VisionSubtitlePicker(subtitles,
        onSelect = { subtitleModel.select(it); choosingSubtitle = false; markInteraction() },
        onRetry = { subtitles.selected?.let(subtitleModel::select) ?: subtitleModel.loadTracks() },
        onDismiss = { choosingSubtitle = false; markInteraction() })
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
                    Text(visionPlaybackDescription(engine, playback), style = MaterialTheme.typography.bodySmall)
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
                if (event.type == KeyEventType.KeyUp && event.key == consumedKey) {
                    consumedKey = null
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val key = visionPlaybackKey(event)
                val action = playbackKeyAction(key, controlsVisible || error != null)
                if (action == PlaybackKeyAction.PASS) {
                    if (key != PlaybackKey.OTHER) lastInteractionTime = System.currentTimeMillis()
                    false
                } else {
                    consumedKey = event.key
                    when (action) {
                        PlaybackKeyAction.SHOW_CONTROLS -> markInteraction()
                        PlaybackKeyAction.TOGGLE -> togglePlayback()
                        PlaybackKeyAction.PLAY, PlaybackKeyAction.PAUSE -> {
                            markInteraction()
                            if (canControl) {
                                wantsPlayback = action == PlaybackKeyAction.PLAY
                                if (playbackShouldRun(wantsPlayback, foreground, canControl)) player.play() else player.pause()
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
        VisionVideoViewport(viewport, videoZoomed, VisionPreferences.videoZoomEnabled) { videoModifier ->
            key(player) {
                VisionPlayerSurface(player, videoModifier)
            }
        }
        key(danmakuHost) {
            VisionDanmakuLayer(danmakuHost, Modifier.size(viewport.width.dp, viewport.height.dp))
        }
        val subtitleText = remember(subtitles.cues, elapsedMs) { activeSubtitle(subtitles.cues, elapsedMs) }
        if (subtitleText.isNotBlank()) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp)
                .padding(bottom = if (controlsVisible) 260.dp else 40.dp).widthIn(max = 1000.dp),
                color = Color.Black.copy(alpha = 0.8f), shape = MaterialTheme.shapes.small) {
                Text(subtitleText, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
            }
        }
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
                            scrubFraction?.let { seekTo((it * durationMs).toLong()) }
                            scrubFraction = null
                            markInteraction()
                        },
                        enabled = canControl && durationMs > 0,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "播放进度" },
                    )
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            markInteraction()
                            VisionPreferences.updateDanmakuEnabled(!VisionPreferences.danmakuEnabled)
                        }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(if (VisionPreferences.danmakuEnabled) "弹幕：开" else "弹幕：关")
                        }
                        if (VisionUserSession.isLoggedIn) FilledTonalButton(onClick = {
                            markInteraction()
                            danmakuProgress = elapsedMs
                            sendingDanmaku = true
                        }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text("发送弹幕")
                        }
                        FilledTonalButton(onClick = { markInteraction(); choosingSubtitle = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(if (subtitles.selected == null) "CC 字幕" else "CC · ${subtitles.selected?.label.orEmpty()}")
                        }
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
                        if (VisionPreferences.videoZoomEnabled) FilledTonalButton(onClick = { markInteraction(); videoZoomed = !videoZoomed }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(if (videoZoomed) "还原视频" else "放大视频")
                        }
                    }
                }
            }
        }
        if (sendingDanmaku) VisionDanmakuComposerOverlay(
            danmakuProgress, activeCid, video.bvid,
            onDismiss = { sendingDanmaku = false; markInteraction() },
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(6f),
        )
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
