package com.qx.orbit.bili.presentation.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.api.CookiesApi
import com.qx.orbit.bili.data.api.DanmakuApi
import com.qx.orbit.bili.data.api.HeartbeatApi
import com.qx.orbit.bili.data.api.HistoryApi
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.util.SharedPreferencesUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IjkMediaPlayer

@Composable
fun PlayerScreen(
    initialData: PlayerData,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var playerData by remember { mutableStateOf(initialData) }
    var showDanmaku by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_danmaku_default_show", true)) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentProgress by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var isLongPressSpeedUp by remember { mutableStateOf(false) }

    val mediaPlayer = remember { IjkMediaPlayer() }
    val danmakuView = remember { DanmakuView(context) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var videoWidth by remember { mutableFloatStateOf(16f) }
    var videoHeight by remember { mutableFloatStateOf(9f) }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        if (isPrepared) {
            if (isPlaying) danmakuView.resume() else danmakuView.pause()
        }
        while(isPlaying && isPrepared) {
            currentProgress = mediaPlayer.currentPosition
            delay(1000)
        }
    }

    LaunchedEffect(showDanmaku) {
        if (showDanmaku) danmakuView.show() else danmakuView.hide()
    }

    LaunchedEffect(playerData.cid) {
        isLoading = true
        try {
            if (!CookieManager.getCookie().contains("buvid3")) {
                CookiesApi.checkCookies()
            }
            
            val danmakuSegment = DanmakuApi.getVideoDanmakuSegment(playerData.aid, playerData.cid, 1)
            val parser = BiliProtobufDanmakuParser()
            if (danmakuSegment != null) {
                parser.setDanmakuSegments(listOf(danmakuSegment))
            }
            val danmakuContext = DanmakuContext.create().apply {
                val mergeDuplicates = SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false)
                val allowOverlap = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
                val maxLines = SharedPreferencesUtil.getInt("player_danmaku_maxline", 0)

                setDuplicateMergingEnabled(mergeDuplicates)
                
                if (!allowOverlap) {
                    val overlappingPairs = mapOf(1 to true, 5 to true, 4 to true, 6 to true)
                    preventOverlapping(overlappingPairs)
                }

                if (maxLines > 0) {
                    val maxLinesPair = mapOf(1 to maxLines, 5 to maxLines, 4 to maxLines, 6 to maxLines)
                    setMaximumLines(maxLinesPair)
                }

                setScaleTextSize(0.8f)
                setDanmakuTransparency(0.4f)
            }
            danmakuView.prepare(parser, danmakuContext)
            danmakuView.enableDanmakuDrawingCache(true)

            val result = PlayerApi.getVideo(playerData)
            playerData = result
            if (result.videoUrl.isNotEmpty()) {
                mediaPlayer.reset()
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "file,http,https,tcp,tls,crypto")
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", "Referer: https://www.bilibili.com")
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1)
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1)
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 2)
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
                mediaPlayer.dataSource = result.videoUrl
                surfaceHolder?.let { mediaPlayer.setDisplay(it) }
                if (com.qx.orbit.bili.util.SharedPreferencesUtil.getBoolean("player_loop", false)) {
                    mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "loop", 1)
                }
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener {
                    isPrepared = true
                    isLoading = false
                    totalDuration = it.duration
                    it.start()
                    isPlaying = true
                    danmakuView.start()
                    
                    // Report heartbeat on start
                    scope.launch {
                        try {
                            HeartbeatApi.reportHeartbeat(
                                aid = playerData.aid,
                                bvid = playerData.bvid,
                                cid = playerData.cid,
                                playedTime = 0
                            )
                        } catch (e: Exception) {}
                    }
                }
                mediaPlayer.setOnVideoSizeChangedListener { _, width, height, _, _ ->
                    if (width > 0 && height > 0) {
                        videoWidth = width.toFloat()
                        videoHeight = height.toFloat()
                    }
                }
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    errorMessage = "播放器错误: $what"
                    true
                }
                mediaPlayer.setOnInfoListener { _, what, _ ->
                    when (what) {
                        IjkMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            danmakuView.pause()
                        }
                        IjkMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                            if (isPlaying) {
                                danmakuView.resume()
                            }
                        }
                    }
                    true
                }
            } else {
                errorMessage = "无法获取视频地址"
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = "网络请求失败"
            isLoading = false
        }
    }

    LaunchedEffect(isLongPressSpeedUp) {
        if (isLongPressSpeedUp) {
            try { mediaPlayer.setSpeed(2.0f) } catch(e:Exception){}
            try { danmakuView.setSpeed(2.0f) } catch(e:Exception){}
        } else {
            try { mediaPlayer.setSpeed(1.0f) } catch(e:Exception){}
            try { danmakuView.setSpeed(1.0f) } catch(e:Exception){}
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val startTs = System.currentTimeMillis() / 1000
        onDispose {
            view.keepScreenOn = false
            var currentPosSeconds = 0L
            try {
                currentPosSeconds = mediaPlayer.currentPosition / 1000
            } catch (e: Exception) {}

            kotlinx.coroutines.GlobalScope.launch {
                try {
                    HistoryApi.reportHistory(playerData.aid, playerData.cid, currentPosSeconds)
                    HeartbeatApi.reportHeartbeat(
                        aid = playerData.aid,
                        bvid = playerData.bvid,
                        cid = playerData.cid,
                        playedTime = currentPosSeconds,
                        startTs = startTs
                    )
                } catch (e: Exception) {}
            }
            mediaPlayer.release()
            danmakuView.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (SharedPreferencesUtil.getBoolean("player_scale", true)) {
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val allowMove = SharedPreferencesUtil.getBoolean("player_doublemove", true)
                            if (allowMove || zoom == 1f) {
                                val maxX = (size.width * (scale - 1)) / 2
                                val maxY = (size.height * (scale - 1)) / 2
                                offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                            }
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val timeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var isUp = false
                        while (!isUp) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToUp() }) isUp = true
                        }
                    }
                    if (timeout == null) {
                        if (isPlaying && !showControls && SharedPreferencesUtil.getBoolean("player_longclick", true)) isLongPressSpeedUp = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToUp() }) {
                                isLongPressSpeedUp = false
                                break
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            danmakuView.pause()
                            isPlaying = false
                            // Report progress on pause
                            scope.launch {
                                try {
                                    val pos = mediaPlayer.currentPosition / 1000
                                    HistoryApi.reportHistory(playerData.aid, playerData.cid, pos)
                                    HeartbeatApi.reportHeartbeat(
                                        aid = playerData.aid,
                                        bvid = playerData.bvid,
                                        cid = playerData.cid,
                                        playedTime = pos
                                    )
                                } catch (_: Exception) {}
                            }
                        } else {
                            mediaPlayer.start()
                            danmakuView.resume()
                            isPlaying = true
                        }
                        showControls = true
                    }
                )
            }
            .onRotaryScrollEvent {
                if (isPrepared) {
                    val delta = it.verticalScrollPixels
                    val newProgress = (mediaPlayer.currentPosition + delta * 100).toLong().coerceIn(0L, totalDuration)
                    mediaPlayer.seekTo(newProgress)
                    danmakuView.seekTo(newProgress)
                    currentProgress = newProgress
                    showControls = true
                }
                true
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            // Video Surface
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {
                                    surfaceHolder = h
                                    if (isPrepared) mediaPlayer.setDisplay(h)
                                }
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
                                override fun surfaceDestroyed(h: SurfaceHolder) {
                                    surfaceHolder = null
                                    mediaPlayer.setDisplay(null)
                                }
                            })
                        }
                    },
                    modifier = Modifier.aspectRatio(
                        ratio = if (videoWidth > 0 && videoHeight > 0) videoWidth / videoHeight else 16f / 9f,
                        matchHeightConstraintsFirst = videoHeight > videoWidth
                    )
                )
            }

            AndroidView(
                factory = { danmakuView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        errorMessage?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x88000000))
                    .padding(8.dp)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .pointerInput(showControls) {
                        if (showControls) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragProgress = (currentProgress.toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    danmakuView.pause()
                                },
                                onDragEnd = {
                                    if (dragProgress >= 0f) {
                                        val targetTime = (dragProgress * totalDuration).toLong()
                                        mediaPlayer.seekTo(targetTime)
                                        danmakuView.seekTo(targetTime)
                                        currentProgress = targetTime
                                        dragProgress = -1f
                                        if (isPlaying) danmakuView.resume()
                                    }
                                },
                                onDragCancel = {
                                    dragProgress = -1f
                                    if (isPlaying) danmakuView.resume()
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val progressDelta = dragAmount / (size.width.toFloat() * 0.8f) // use 80% screen width as full seek bar
                                    dragProgress = (dragProgress + progressDelta).coerceIn(0f, 1f)
                                }
                            )
                        }
                    }
            ) {
                Text(
                    text = playerData.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                        .clickable { onBack() }
                        .basicMarquee()
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                danmakuView.pause()
                            } else {
                                mediaPlayer.start()
                                danmakuView.resume()
                            }
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (SharedPreferencesUtil.getBoolean("player_ui_showDanmakuBtn", true)) {
                        IconButton(
                            onClick = { showDanmaku = !showDanmaku },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text(
                                text = "弹",
                                color = if (showDanmaku) Color.White else Color.Gray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val displayProgress = if (dragProgress >= 0f) dragProgress else (currentProgress.toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                val displayTimeMs = if (dragProgress >= 0f) (dragProgress * totalDuration).toLong() else currentProgress

                val formatTime = { timeMs: Long ->
                    val totalSeconds = timeMs / 1000
                    val m = totalSeconds / 60
                    val s = totalSeconds % 60
                    "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(displayProgress)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "${formatTime(displayTimeMs)} / ${formatTime(totalDuration)}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}