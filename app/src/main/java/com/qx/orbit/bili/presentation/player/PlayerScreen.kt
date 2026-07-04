package com.qx.orbit.bili.presentation.player

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
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
import com.qx.orbit.bili.data.api.LiveApi
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.util.SharedPreferencesUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import master.flame.danmaku.danmaku.loader.android.*
import master.flame.danmaku.danmaku.model.android.*
import master.flame.danmaku.danmaku.parser.android.*
import master.flame.danmaku.danmaku.parser.*
import master.flame.danmaku.ui.widget.DanmakuView
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import kotlin.time.Duration.Companion.seconds
import androidx.core.net.toUri
import androidx.wear.compose.material3.MaterialTheme
import com.qx.orbit.bili.presentation.viewmodel.EmoteInline
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.io.File

@Suppress("unused")
@OptIn(DelicateCoroutinesApi::class)
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
    val isLive = playerData.type == PlayerData.TYPE_LIVE
    val isLocal = playerData.type == PlayerData.TYPE_LOCAL
    var showDanmaku by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_danmaku_default_show", true)) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var interactionCounter by remember { mutableIntStateOf(0) }
    var currentProgress by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bufferSpeed by remember { mutableStateOf("") }
    
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var isLongPressSpeedUp by remember { mutableStateOf(false) }
    var longPressUpTimestamp by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var liveElapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isLive, playerData.timeStamp) {
        if (isLive && playerData.timeStamp > 0) {
            while (true) {
                liveElapsedSeconds = (System.currentTimeMillis() / 1000) - playerData.timeStamp
                delay(1.seconds)
            }
        }
    }

    val mediaPlayer = remember { IjkMediaPlayer() }
    val danmakuView = remember { DanmakuView(context) }
    var danmakuContext by remember { mutableStateOf<DanmakuContext?>(null) }
    var liveWebSocket by remember { mutableStateOf<WebSocket?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }
    var textureSurface by remember { mutableStateOf<Surface?>(null) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var videoWidth by remember { mutableFloatStateOf(16f) }
    var videoHeight by remember { mutableFloatStateOf(9f) }
    val useTextureView = remember { SharedPreferencesUtil.getBoolean("player_texture_view", false) }

    LaunchedEffect(interactionCounter) {
        showControls = true
        delay(3.seconds)
        showControls = false
    }

    LaunchedEffect(isPlaying, isPrepared) {
        if (isPrepared) {
            if (isPlaying) danmakuView.resume() else danmakuView.pause()
        }
        while(isPlaying && isPrepared) {
            currentProgress = mediaPlayer.currentPosition
            delay(1.seconds)
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (isLoading) {
                val speedBytes = mediaPlayer.tcpSpeed
                bufferSpeed = if (speedBytes > 0) {
                    when {
                        speedBytes >= 1024 * 1024 -> String.format("%.1f MB/s", speedBytes / (1024f * 1024f))
                        speedBytes >= 1024 -> String.format("%.1f KB/s", speedBytes / 1024f)
                        else -> "$speedBytes B/s"
                    }
                } else {
                    if (!isPrepared) "加载中..." else "缓冲中..."
                }
                delay(1.seconds)
            }
        } else {
            bufferSpeed = ""
        }
    }

    LaunchedEffect(showDanmaku) {
        if (showDanmaku) danmakuView.show() else danmakuView.hide()
    }

    LaunchedEffect(if (isLive) playerData.aid else if (isLocal) playerData.videoUrl else playerData.cid) {
        isLoading = true
        try {
            if (!isLocal && !CookieManager.getCookie().contains("buvid3")) {
                CookiesApi.checkCookies()
            }

            if (!isLive && !isLocal) {
                val danmakuSegment = DanmakuApi.getVideoDanmakuSegment(playerData.aid, playerData.cid, 1)
                val parser = BiliProtobufDanmakuParser()
                if (danmakuSegment != null) {
                    parser.setDanmakuSegments(listOf(danmakuSegment))
                }
                val ctx = DanmakuContext.create().apply {
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
                danmakuContext = ctx
                danmakuView.prepare(parser, ctx)
                danmakuView.enableDanmakuDrawingCache(true)
            } else if (isLocal) {
                val ctx = DanmakuContext.create().apply {
                    setDuplicateMergingEnabled(false)
                    setScaleTextSize(0.8f)
                    setDanmakuTransparency(0.4f)
                }
                danmakuContext = ctx
                var xmlFile = File("${playerData.videoUrl}.danmaku.xml")
                if (!xmlFile.exists()) {
                    val fallbackPath = playerData.videoUrl.replace(".mp4", ".danmaku.xml").replace(".m4s", ".danmaku.xml")
                    xmlFile = File(fallbackPath.toUri().path ?: "")
                }
                if (xmlFile.exists()) {
                    val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
                    loader.load(xmlFile.inputStream())
                    val parser = BiliDanmukuParser()
                    parser.load(loader.dataSource)
                    danmakuView.prepare(parser, ctx)
                } else {
                    danmakuView.prepare(object : BaseDanmakuParser() {
                        override fun parse(): Danmakus {
                            return Danmakus()
                        }
                    }, ctx)
                }
                danmakuView.enableDanmakuDrawingCache(true)
            } else {
                val ctx = DanmakuContext.create().apply {
                    setDuplicateMergingEnabled(false)
                    setScaleTextSize(0.8f)
                    setDanmakuTransparency(0.4f)
                }
                danmakuContext = ctx
                danmakuView.prepare(object : BaseDanmakuParser() {
                    override fun parse(): Danmakus {
                        return Danmakus()
                    }
                }, ctx)
                danmakuView.enableDanmakuDrawingCache(true)
            }

            val result = if (isLive || isLocal) playerData 
                         else if (playerData.type == PlayerData.TYPE_BANGUMI) PlayerApi.getBangumi(playerData)
                         else PlayerApi.getVideo(playerData)
            if (!isLive && !isLocal) playerData = result
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
                if (isLive) {
                    mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "live直播延时", 1)
                    mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
                    mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)
                }
                mediaPlayer.dataSource = result.videoUrl
                if (useTextureView) {
                    if (textureSurface != null) {
                        mediaPlayer.setSurface(textureSurface)
                    }
                } else {
                    if (surfaceHolder != null) {
                        mediaPlayer.setDisplay(surfaceHolder)
                    }
                }
                if (SharedPreferencesUtil.getBoolean("player_loop", false)) {
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
                    
                    if (!isLive && playerData.type != PlayerData.TYPE_LOCAL) {
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
                            isLoading = true
                        }
                        IjkMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                            if (isPlaying) {
                                danmakuView.resume()
                            }
                            isLoading = false
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

    // Live danmaku WebSocket connection
    LaunchedEffect(isLive, isPrepared) {
        if (isLive && isPrepared) {
            try {
                val danmuInfo = LiveApi.getDanmuInfo(playerData.aid)
                val host = danmuInfo?.host_list?.firstOrNull()
                val token = danmuInfo?.token
                if (host != null && token != null) {
                    val url = "wss://${host.host}:${host.wss_port}/sub"
                    val buvid = CookieManager.getCookie().split("; ")
                        .find { it.startsWith("buvid3=") }?.substringAfter("=") ?: ""
                    val mid = CookieManager.getMid()

                    val callback = object : PlayerCallback {
                        @SuppressLint("LocalContextResourcesRead")
                        override fun addDanmaku(text: String, color: Int, textSize: Int, type: Int, borderColor: Int, senderName: String, emotes: Map<String, EmoteInline>?, singleEmote: EmoteInline?, id: String) {
                            try {
                                val ctx = danmakuContext ?: return
                                val item = ctx.mDanmakuFactory.createDanmaku(type) ?: return
                                val showSender = SharedPreferencesUtil.getBoolean("player_danmaku_showsender", true)
                                val displayText = if (!showSender && senderName.isNotEmpty()) {
                                    text.removePrefix("$senderName：")
                                } else text
                                item.text = displayText
                                item.padding = 5
                                item.priority = 1
                                item.textColor = color
                                item.textSize = textSize * (context.resources.displayMetrics.density - 0.6f)
                                item.time = danmakuView.currentTime + 100
                                danmakuView.addDanmaku(item)
                            } catch (_: Exception) {}
                        }
                        override var onlineNumber: String = ""
                        override fun updateTitle(title: String) {}
                    }

                    val listener = PlayerDanmuClientListener(
                        roomId = playerData.aid,
                        uid = mid,
                        buvid = buvid,
                        key = token,
                        callback = callback
                    )

                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url(url)
                        .header("Cookie", CookieManager.getCookie())
                        .header("Origin", "https://live.bilibili.com")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                        .build()
                    liveWebSocket = client.newWebSocket(request, listener)
                    Log.d("BiliApi", "PlayerScreen danmaku WS connecting to $url, uid=$mid, roomid=${playerData.aid}")
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isLongPressSpeedUp, playbackSpeed) {
        if (isLongPressSpeedUp) {
            try { mediaPlayer.setSpeed(2.0f) } catch(e:Exception){}
            try { danmakuView.setSpeed(2.0f) } catch(e:Exception){}
        } else {
            try { mediaPlayer.setSpeed(playbackSpeed) } catch(e:Exception){}
            try { danmakuView.setSpeed(playbackSpeed) } catch(e:Exception){}
        }
    }

    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val startTs = System.currentTimeMillis() / 1000
        onDispose {
            view.keepScreenOn = false

            if (!isLive && playerData.type != PlayerData.TYPE_LOCAL) {
                var currentPosSeconds = 0L
                try {
                    currentPosSeconds = mediaPlayer.currentPosition / 1000
                } catch (e: Exception) {}

                GlobalScope.launch {
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
            }

            liveWebSocket?.close(1000, "bye")
            liveWebSocket = null
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
                var wasLongPress: Boolean
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
                        wasLongPress = true
                        if (isPlaying && !showControls && !isLive && SharedPreferencesUtil.getBoolean("player_longclick", true)) isLongPressSpeedUp = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToUp() }) {
                                isLongPressSpeedUp = false
                                break
                            }
                        }
                    } else {
                        wasLongPress = false
                        val upEvent = currentEvent.changes.firstOrNull()
                        val upTime = upEvent?.uptimeMillis ?: 0L
                        val downTime = down.uptimeMillis
                        val duration = upTime - downTime
                        if (duration < viewConfiguration.longPressTimeoutMillis && duration > 0) {
                            val downPos = down.position
                            val upPos = upEvent?.position ?: downPos
                            val dist = (upPos - downPos).getDistance()
                            if (dist < viewConfiguration.touchSlop) {
                                if (!wasLongPress) {
                                    if (showControls) showControls = false
                                    else interactionCounter++
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            danmakuView.pause()
                            isPlaying = false
                            // Report progress on pause
                            scope.launch {
                                if (playerData.type == PlayerData.TYPE_LOCAL || isLive) return@launch
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
                        interactionCounter++
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
                    interactionCounter++
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
                ),
            contentAlignment = Alignment.Center
        ) {
            // Video Surface - fit within round screen safe area by default
            Box(modifier = Modifier.fillMaxSize(0.86524f)) {
                AndroidView(
                    factory = { ctx ->
                        if (useTextureView) {
                            TextureView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                ).apply {
                                    gravity = Gravity.CENTER
                                }
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        surfaceReady = true
                                        val s = Surface(surface)
                                        textureSurface = s
                                        mediaPlayer.setSurface(s)
                                    }

                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        surfaceReady = false
                                        textureSurface = null
                                        mediaPlayer.setSurface(null)
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        } else {
                            SurfaceView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                ).apply {
                                    gravity = Gravity.CENTER
                                }
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) {
                                        surfaceHolder = h
                                        surfaceReady = true
                                        mediaPlayer.setDisplay(h)
                                    }
                                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
                                    override fun surfaceDestroyed(h: SurfaceHolder) {
                                        surfaceHolder = null
                                        surfaceReady = false
                                        mediaPlayer.setDisplay(null)
                                    }
                                })
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(
                            ratio = if (videoWidth > 0 && videoHeight > 0) videoWidth / videoHeight else 16f / 9f,
                            matchHeightConstraintsFirst = videoHeight > videoWidth
                        )
                )
            }
        }

        // DanmakuView outside scaling container to prevent scaling with video
        AndroidView(
            factory = { danmakuView },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                if (bufferSpeed.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = bufferSpeed,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (isLongPressSpeedUp) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "倍速播放中",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
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
                                    interactionCounter++
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
                    if (!isLive) {
                        IconButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-16).dp).size(36.dp)
                        ) {
                            val iconRes = when (playbackSpeed) {
                                0.5f -> R.drawable.speed_0_5x
                                1.5f -> R.drawable.speed_1_5x
                                2.0f -> R.drawable.speed_2x
                                else -> R.drawable.speed_1x
                            }
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = "Playback Speed",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                danmakuView.pause()
                                isPlaying = false
                                scope.launch {
                                    if (playerData.type == PlayerData.TYPE_LOCAL || isLive) return@launch
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
                        },
                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                    ) {
                        Icon(
                            imageVector = (if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow),
                            contentDescription = "Play/Pause",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    if (SharedPreferencesUtil.getBoolean("player_ui_showDanmakuBtn", true)) {
                        IconButton(
                            onClick = { showDanmaku = !showDanmaku },
                            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp).size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (showDanmaku) R.drawable.ic_danmaku_inline_switch_v2_on else R.drawable.ic_danmaku_inline_switch_v2_off),
                                contentDescription = "Toggle Danmaku",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                val formatTime = { timeMs: Long ->
                    val totalSeconds = timeMs / 1000
                    val h = totalSeconds / 3600
                    val m = (totalSeconds % 3600) / 60
                    val s = totalSeconds % 60
                    if (h > 0) "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
                    else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    if (isLive) {
                        Text(
                            text = if (playerData.timeStamp > 0) "直播 ${formatTime(liveElapsedSeconds * 1000)}"
                            else "直播中",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    } else {
                        val displayProgress = if (dragProgress >= 0f) dragProgress else (currentProgress.toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val displayTimeMs = if (dragProgress >= 0f) (dragProgress * totalDuration).toLong() else currentProgress

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
}