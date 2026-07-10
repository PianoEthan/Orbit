package com.qx.orbit.bili.presentation.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.widget.FrameLayout
import coil.imageLoader
import coil.compose.AsyncImage
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.material3.VolumeScreen
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.api.CookiesApi
import com.qx.orbit.bili.data.api.DanmakuApi
import com.qx.orbit.bili.data.api.HeartbeatApi
import com.qx.orbit.bili.data.api.HistoryApi
import com.qx.orbit.bili.data.api.LiveApi
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.findActivity
import com.qx.orbit.bili.presentation.viewmodel.EmoteInline
import com.qx.orbit.bili.service.PlayerForegroundService
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.TextureViewProbe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.qx.orbit.bili.util.danmaku.base.DanmakuConfig
import com.qx.orbit.bili.util.danmaku.base.DanmakuPlayer
import com.qx.orbit.bili.util.danmaku.base.ProtobufDanmakuParser
import com.qx.orbit.bili.util.danmaku.base.createDanmaku
import com.qx.orbit.bili.util.danmaku.base.createDanmakuConfig
import com.qx.orbit.bili.util.danmaku.base.createDanmakuPlayer
import com.qx.orbit.bili.util.danmaku.base.createEmptyParser
import com.qx.orbit.bili.util.danmaku.base.createProtobufParser
import com.qx.orbit.bili.util.danmaku.base.createXmlParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
@OptIn(ExperimentalHorologistApi::class, DelicateCoroutinesApi::class)
@SuppressLint("DefaultLocale")
@Composable
fun PlayerScreen(
    initialData: PlayerData,
    onBack: () -> Unit,
    onDisposeAction: (epid: Long, progress: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }
    
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            onBack()
        } else {
            backPressedTime = currentTime
            RoundToast.show(context, "再按一次返回键退出")
        }
    }
    val scope = rememberCoroutineScope()
    
    var playerData by remember { mutableStateOf(initialData) }
    val isLive = playerData.type == PlayerData.TYPE_LIVE
    val isLocal = playerData.type == PlayerData.TYPE_LOCAL
    var showDanmaku by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_danmaku_default_show", true)) }
    var isPlaying by remember { mutableStateOf(false) }
    
    val leftBtnAction by remember { mutableIntStateOf(SharedPreferencesUtil.getInt("player_custom_btn_left", 2)) }
    val rightBtnAction by remember { mutableIntStateOf(SharedPreferencesUtil.getInt("player_custom_btn_right", 1)) }
    var showVolumeScreen by remember { mutableStateOf(false) }
    val volumeFocusRequester = remember { FocusRequester() }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val isAutoLandscape = remember { SharedPreferencesUtil.getBoolean("player_autolandscape", false) }
    DisposableEffect(isAutoLandscape) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        if (isAutoLandscape && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (isAutoLandscape && activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    var isPrepared by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isAudioOnlyMode by remember { mutableStateOf(false) }
    var switchPendingSeekMs by remember { mutableLongStateOf(-1L) }
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
    val danmakuPlayer = remember { createDanmakuPlayer(context) }
    var danmakuConfig by remember { mutableStateOf<DanmakuConfig?>(null) }

    val mediaSession = remember {
        MediaSession(context, "OrbitPlayer").apply {
            isActive = true
        }
    }

    val audioFocusListener = remember { AudioManager.OnAudioFocusChangeListener { } }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    DisposableEffect(Unit) {
        val callback = object : MediaSession.Callback() {
            override fun onPlay() {
                mediaPlayer.start()
                danmakuPlayer.resume()
                isPlaying = true
            }

            override fun onPause() {
                mediaPlayer.pause()
                danmakuPlayer.pause()
                isPlaying = false
            }

            override fun onSeekTo(pos: Long) {
                mediaPlayer.seekTo(pos)
                danmakuPlayer.seekTo(pos)
                currentProgress = pos
            }
            
            override fun onSkipToNext() {
                if (playerData.currentPageIndex + 1 < playerData.cids.size) {
                    val nextIndex = playerData.currentPageIndex + 1
                    val nextAid = if (playerData.aids.size > nextIndex) playerData.aids[nextIndex] else playerData.aid
                    val nextCid = playerData.cids[nextIndex]
                    val nextEpid = if (playerData.epids.size > nextIndex) playerData.epids[nextIndex] else playerData.epid
                    val nextTitle = if (playerData.pagenames.size > nextIndex) playerData.pagenames[nextIndex] else playerData.title
                    playerData = playerData.copy(
                        currentPageIndex = nextIndex,
                        aid = nextAid,
                        cid = nextCid,
                        epid = nextEpid,
                        title = nextTitle,
                        progress = 0
                    )
                }
            }
            
            override fun onSkipToPrevious() {
                if (playerData.currentPageIndex - 1 >= 0) {
                    val prevIndex = playerData.currentPageIndex - 1
                    val prevAid = if (playerData.aids.size > prevIndex) playerData.aids[prevIndex] else playerData.aid
                    val prevCid = playerData.cids[prevIndex]
                    val prevEpid = if (playerData.epids.size > prevIndex) playerData.epids[prevIndex] else playerData.epid
                    val prevTitle = if (playerData.pagenames.size > prevIndex) playerData.pagenames[prevIndex] else playerData.title
                    playerData = playerData.copy(
                        currentPageIndex = prevIndex,
                        aid = prevAid,
                        cid = prevCid,
                        epid = prevEpid,
                        title = prevTitle,
                        progress = 0
                    )
                }
            }
        }
        mediaSession.setCallback(callback)
        onDispose {
            val stopIntent = Intent(context, PlayerForegroundService::class.java).apply {
                action = "STOP"
            }
            try {
                context.startService(stopIntent)
            } catch (e: Exception) {}
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001)
            mediaSession.isActive = false
            mediaSession.release()
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    LaunchedEffect(isPlaying, isPrepared, playbackSpeed, playerData.title, totalDuration) {
        if (isPrepared) {
            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, playerData.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Orbit")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, playerData.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Orbit")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Orbit")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, totalDuration)
                
            if (playerData.cover.isNotEmpty()) {
                try {
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(playerData.cover)
                        .size(512)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val drawable = result.drawable
                        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                            drawable.bitmap
                        } else null
                        if (bitmap != null) {
                            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                        }
                    }
                } catch (e: Exception) {}
            }
            mediaSession.setMetadata(metadataBuilder.build())

            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val playbackState = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
                .setState(state, currentProgress, playbackSpeed)
                .build()
            mediaSession.setPlaybackState(playbackState)
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel("orbit_player", "播放控制", NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }
            val activityClass = context.findActivity()?.javaClass
            val pendingIntent = if (activityClass != null) {
                val intent = Intent(context, activityClass).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            } else null
            
            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Notification.Builder(context, "orbit_player")
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(playerData.title)
                .setContentText(if (isPlaying) "正在播放" else "已暂停")
                .setOngoing(isPlaying)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC).style = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }
            val intent = Intent(context, PlayerForegroundService::class.java).apply {
                putExtra("title", playerData.title)
                putExtra("isPlaying", isPlaying)
                putExtra("token", mediaSession.sessionToken)
            }
            if (SharedPreferencesUtil.getBoolean("player_background", false)) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                notificationManager.notify(1001, builder.build())
            }
        }
    }

    LaunchedEffect(currentProgress) {
        if (isPrepared) {
            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val playbackState = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
                .setState(state, currentProgress, playbackSpeed)
                .build()
            mediaSession.setPlaybackState(playbackState)
        }
    }

    var liveWebSocket by remember { mutableStateOf<WebSocket?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var surfaceReady by remember { mutableStateOf(false) }
    var textureSurface by remember { mutableStateOf<Surface?>(null) }
    val isTextureViewOk = remember { mutableIntStateOf(SharedPreferencesUtil.getInt("isTextureViewOk", 0)) }
    val showTextureDialog = remember { mutableStateOf(false) }
    val probeFailedThisSession = remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var videoWidth by remember { mutableFloatStateOf(16f) }
    var videoHeight by remember { mutableFloatStateOf(9f) }
    val useTextureView = remember { SharedPreferencesUtil.getBoolean("player_texture_view", true) }
    val effectiveUseTexture = useTextureView && isTextureViewOk.intValue != -1

    // Prefs 已记录 TextureView 不可用 → 弹 dialog（允许 retry）
    LaunchedEffect(Unit) {
        if (useTextureView && isTextureViewOk.intValue == -1) {
            showTextureDialog.value = true
        }
    }

    LaunchedEffect(interactionCounter, isPlaying) {
        if (interactionCounter > 0 || !isPlaying) {
            showControls = true
        }
        if (isPlaying) {
            delay(3.seconds)
            showControls = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        if (isPrepared) {
            if (isPlaying) danmakuPlayer.resume() else danmakuPlayer.pause()
        }
        var heartbeatTick = 0
        while(isPlaying && isPrepared) {
            currentProgress = mediaPlayer.currentPosition
            
            if (heartbeatTick >= 15) {
                heartbeatTick = 0
                val pos = currentProgress / 1000
                if (pos > 0 && playerData.type != PlayerData.TYPE_LOCAL && !isLive) {
                    try {
                        val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                        if (!isBangumi) {
                            HistoryApi.reportHistory(playerData.aid, playerData.cid, pos)
                        }
                        HeartbeatApi.reportHeartbeat(
                            aid = playerData.aid,
                            bvid = playerData.bvid,
                            cid = playerData.cid,
                            playedTime = pos,
                            type = if (isBangumi) "4" else "3",
                            subType = if (isBangumi) "1" else null,
                            epid = if (isBangumi) playerData.epid else null,
                            sid = if (isBangumi) playerData.sid else null,
                            videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                        )
                    } catch (_: Exception) {}
                }
            }
            heartbeatTick++
            
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
        if (showDanmaku) danmakuPlayer.show() else danmakuPlayer.hide()
    }

    LaunchedEffect(if (isLive) playerData.aid else if (isLocal) playerData.videoUrl else playerData.cid, isAudioOnlyMode) {
        isLoading = true
        try {
            if (!isLocal && !CookieManager.getCookie().contains("buvid3")) {
                CookiesApi.checkCookies()
            }

            if (!isLive && !isLocal) {
                val danmakuSegment = DanmakuApi.getVideoDanmakuSegment(playerData.aid, playerData.cid, 1)
                val parser = createProtobufParser()
                if (danmakuSegment != null) {
                    parser.setDanmakuSegments(listOf(danmakuSegment))
                }
                val config = createDanmakuConfig().apply {
                    val mergeDuplicates = SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false)
                    val allowOverlap = SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true)
                    val maxLines = SharedPreferencesUtil.getInt("player_danmaku_maxline", 0)
                    val enableAdvanced = SharedPreferencesUtil.getBoolean("player_danmaku_advanced_enable", true)

                    setDuplicateMerging(mergeDuplicates)
                    setSpecialDanmakuVisibility(enableAdvanced)

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
                danmakuConfig = config
                danmakuPlayer.prepare(parser, config)
                danmakuPlayer.enableDrawingCache(true)
            } else if (isLocal) {
                val config = createDanmakuConfig().apply {
                    val enableAdvanced = SharedPreferencesUtil.getBoolean("player_danmaku_advanced_enable", true)
                    setSpecialDanmakuVisibility(enableAdvanced)
                    setDuplicateMerging(false)
                    setScaleTextSize(0.8f)
                    setDanmakuTransparency(0.4f)
                }
                danmakuConfig = config
                var xmlFile = File("${playerData.videoUrl}.danmaku.xml")
                if (!xmlFile.exists()) {
                    val fallbackPath = playerData.videoUrl.replace(".mp4", ".danmaku.xml").replace(".m4s", ".danmaku.xml")
                    xmlFile = File(fallbackPath.toUri().path ?: "")
                }
                if (xmlFile.exists()) {
                    val parser = createXmlParser()
                    parser.load(xmlFile.inputStream())
                    danmakuPlayer.prepare(parser, config)
                } else {
                    danmakuPlayer.prepare(createEmptyParser(), config)
                }
                danmakuPlayer.enableDrawingCache(true)
            } else {
                val config = createDanmakuConfig().apply {
                    val enableAdvanced = SharedPreferencesUtil.getBoolean("player_danmaku_advanced_enable", true)
                    setSpecialDanmakuVisibility(enableAdvanced)
                    setDuplicateMerging(false)
                    setScaleTextSize(0.8f)
                    setDanmakuTransparency(0.4f)
                }
                danmakuConfig = config
                danmakuPlayer.prepare(createEmptyParser(), config)
                danmakuPlayer.enableDrawingCache(true)
            }

            val result = if (isLive || isLocal) playerData 
                         else if (playerData.type == PlayerData.TYPE_BANGUMI) {
                             if (isAudioOnlyMode) PlayerApi.getVideoDash(playerData) else PlayerApi.getBangumi(playerData)
                         } else {
                             if (isAudioOnlyMode) PlayerApi.getVideoDash(playerData) else PlayerApi.getVideo(playerData)
                         }
            if (!isLive && !isLocal) playerData = result
            if (result.videoUrl.isNotEmpty() || result.audioUrl.isNotEmpty()) {
                mediaPlayer.reset()
                mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
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
                val playUrl = if (isAudioOnlyMode && result.audioUrl.isNotEmpty()) result.audioUrl else result.videoUrl
                mediaPlayer.dataSource = playUrl
                if (effectiveUseTexture) {
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
                    
                    if (switchPendingSeekMs >= 0) {
                        it.seekTo(switchPendingSeekMs)
                        currentProgress = switchPendingSeekMs
                        switchPendingSeekMs = -1L
                    } else if (playerData.progress > 0) {
                        val targetMs = (playerData.progress * 1000L).coerceAtMost(it.duration)
                        it.seekTo(targetMs)
                        currentProgress = targetMs
                        playerData = playerData.copy(progress = 0)
                    }
                    
                    it.start()
                    isPlaying = true
                    danmakuPlayer.start()
                    
                    if (!isLive && playerData.type != PlayerData.TYPE_LOCAL) {
                        // Report heartbeat on start
                        scope.launch {
                            try {
                                val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                                HeartbeatApi.reportHeartbeat(
                                    aid = playerData.aid,
                                    bvid = playerData.bvid,
                                    cid = playerData.cid,
                                    playedTime = playerData.progress.toLong().coerceAtLeast(0),
                                    type = if (isBangumi) "4" else "3",
                                    subType = if (isBangumi) "1" else null,
                                    epid = if (isBangumi) playerData.epid else null,
                                    sid = if (isBangumi) playerData.sid else null,
                                    videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                                )
                            } catch (e: Exception) {}
                        }
                    }
                }
                mediaPlayer.setOnCompletionListener {
                    scope.launch {
                        val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                        if (isBangumi) {
                            HeartbeatApi.reportHeartbeat(
                                aid = playerData.aid, bvid = playerData.bvid, cid = playerData.cid, playedTime = -1, type = "4", subType = "1", epid = playerData.epid, sid = playerData.sid, videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                            )
                        } else {
                            HistoryApi.reportHistory(playerData.aid, playerData.cid, -1)
                        }
                        
                        if (playerData.currentPageIndex + 1 < playerData.cids.size) {
                            val nextIndex = playerData.currentPageIndex + 1
                            val nextAid = if (playerData.aids.size > nextIndex) playerData.aids[nextIndex] else playerData.aid
                            val nextCid = playerData.cids[nextIndex]
                            val nextEpid = if (playerData.epids.size > nextIndex) playerData.epids[nextIndex] else playerData.epid
                            val nextTitle = if (playerData.pagenames.size > nextIndex) playerData.pagenames[nextIndex] else playerData.title
                            playerData = playerData.copy(
                                currentPageIndex = nextIndex,
                                aid = nextAid,
                                cid = nextCid,
                                epid = nextEpid,
                                title = nextTitle,
                                progress = 0
                            )
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
                            danmakuPlayer.pause()
                            isLoading = true
                        }
                        IjkMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                            if (isPlaying) {
                                danmakuPlayer.resume()
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
                                val config = danmakuConfig ?: return
                                val item = createDanmaku(config, type) ?: return
                                val showSender = SharedPreferencesUtil.getBoolean("player_danmaku_showsender", true)
                                val displayText = if (!showSender && senderName.isNotEmpty()) {
                                    text.removePrefix("$senderName：")
                                } else text
                                item.text = displayText
                                item.padding = 5
                                item.priority = 1
                                item.textColor = color
                                item.textSize = textSize * (context.resources.displayMetrics.density - 0.6f)
                                item.time = danmakuPlayer.getCurrentTime() + 100
                                
                                if (borderColor != 0) {
                                    item.borderColor = borderColor
                                }
                                if (id.isNotEmpty()) {
                                    item.userHash = id
                                }
                                if (singleEmote != null) {
                                    item.obj = singleEmote
                                } else if (emotes != null) {
                                    item.obj = emotes
                                }
                                
                                danmakuPlayer.addDanmaku(item)
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
            try { danmakuPlayer.setSpeed(2.0f) } catch(e:Exception){}
        } else {
            try { mediaPlayer.setSpeed(playbackSpeed) } catch(e:Exception){}
            try { danmakuPlayer.setSpeed(playbackSpeed) } catch(e:Exception){}
        }
    }

    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (SharedPreferencesUtil.getBoolean("player_background", false)) {
                    val audioOnly = SharedPreferencesUtil.getBoolean("player_background_audio_only", false)
                    if (audioOnly && !isLive && !isLocal) {
                        try {
                            switchPendingSeekMs = mediaPlayer.currentPosition
                        } catch (e: Exception) {}
                        isAudioOnlyMode = true
                    } else {
                        mediaPlayer.setSurface(null)
                    }
                } else {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        danmakuPlayer.pause()
                        isPlaying = false
                    }
                }
            } else if (event == Lifecycle.Event.ON_START) {
                if (isAudioOnlyMode) {
                    try {
                        switchPendingSeekMs = mediaPlayer.currentPosition
                    } catch (e: Exception) {}
                    isAudioOnlyMode = false
                } else {
                    if (effectiveUseTexture) {
                        if (textureSurface != null) mediaPlayer.setSurface(textureSurface)
                    } else {
                        if (surfaceHolder != null) mediaPlayer.setDisplay(surfaceHolder)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
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
                
                onDisposeAction(playerData.epid, currentPosSeconds)

                GlobalScope.launch {
                    try {
                        val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                        if (!isBangumi) {
                            HistoryApi.reportHistory(playerData.aid, playerData.cid, currentPosSeconds)
                        }
                        HeartbeatApi.reportHeartbeat(
                            aid = playerData.aid,
                            bvid = playerData.bvid,
                            cid = playerData.cid,
                            playedTime = currentPosSeconds,
                            startTs = startTs,
                            type = if (isBangumi) "4" else "3",
                            subType = if (isBangumi) "1" else null,
                            epid = if (isBangumi) playerData.epid else null,
                            sid = if (isBangumi) playerData.sid else null,
                            videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                        )
                    } catch (e: Exception) {}
                }
            }

            liveWebSocket?.close(1000, "bye")
            liveWebSocket = null
            mediaPlayer.release()
            danmakuPlayer.release()
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
                detectTapGestures(
                    onDoubleTap = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            danmakuPlayer.pause()
                            isPlaying = false
                            // Report progress on pause
                            scope.launch {
                                if (playerData.type == PlayerData.TYPE_LOCAL || isLive) return@launch
                                try {
                                    val pos = mediaPlayer.currentPosition / 1000
                                    val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                                    if (!isBangumi) {
                                        HistoryApi.reportHistory(playerData.aid, playerData.cid, pos)
                                    }
                                    HeartbeatApi.reportHeartbeat(
                                        aid = playerData.aid,
                                        bvid = playerData.bvid,
                                        cid = playerData.cid,
                                        playedTime = pos,
                                        type = if (isBangumi) "4" else "3",
                                        subType = if (isBangumi) "1" else null,
                                        epid = if (isBangumi) playerData.epid else null,
                                        sid = if (isBangumi) playerData.sid else null,
                                        videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                                    )
                                } catch (_: Exception) {}
                            }
                        } else {
                            mediaPlayer.start()
                            danmakuPlayer.resume()
                            isPlaying = true
                        }
                        interactionCounter++
                    }
                )
            }
            .pointerInput(Unit) {
                var wasLongPress: Boolean
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val timeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var isUp = false
                        while (!isUp) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { !it.pressed || it.changedToUp() }) isUp = true
                        }
                    }
                    if (timeout == null) {
                        val timeSinceBack = System.currentTimeMillis() - backPressedTime
                        if (timeSinceBack < 1000) {
                            // Ghost pointer from system edge swipe, ignore it
                        } else {
                            wasLongPress = true
                            if (isPlaying && !showControls && !isLive && SharedPreferencesUtil.getBoolean("player_longclick", true)) isLongPressSpeedUp = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.changes.any { !it.pressed || it.changedToUp() }) {
                                        break
                                    }
                                }
                            } finally {
                                isLongPressSpeedUp = false
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
                                if (!wasLongPress && upEvent?.isConsumed != true) {
                                    if (showControls) showControls = false
                                    else interactionCounter++
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var isUp = false
                        while (!isUp) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.changedToUp() }) isUp = true
                        }
                        true
                    }
                    if (up != null) {
                        val down2 = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (down2 != null) {
                            var isZooming = false
                            var lastY = down2.position.y
                            val startY = down2.position.y
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val changes = event.changes
                                val ptr = changes.firstOrNull { it.id == down2.id }
                                if (ptr == null || !ptr.pressed) break
                                
                                if (!isZooming) {
                                    val dy = ptr.position.y - startY
                                    if (kotlin.math.abs(dy) > viewConfiguration.touchSlop) {
                                        isZooming = true
                                    }
                                }
                                if (isZooming) {
                                    if (SharedPreferencesUtil.getBoolean("player_one_finger_zoom", false)) {
                                        val dy = ptr.position.y - lastY
                                        val zoomFactor = 1f + dy * 0.005f
                                        scale = (scale * zoomFactor).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            val maxX = (size.width * (scale - 1)) / 2f
                                            val maxY = (size.height * (scale - 1)) / 2f
                                            offsetX = offsetX.coerceIn(-maxX, maxX)
                                            offsetY = offsetY.coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        lastY = ptr.position.y
                                        ptr.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .onRotaryScrollEvent {
                if (isPrepared) {
                    val delta = it.verticalScrollPixels
                    val newProgress = (mediaPlayer.currentPosition + delta * 100).toLong().coerceIn(0L, totalDuration)
                    mediaPlayer.seekTo(newProgress)
                    danmakuPlayer.seekTo(newProgress)
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
            Box(
                modifier = Modifier.fillMaxSize(0.86524f),
                contentAlignment = Alignment.Center
            ) {
                if (isAudioOnlyMode || playerData.audioUrl == "audio") {
                    AsyncImage(
                        model = playerData.cover.replace("http://", "https://"),
                        contentDescription = "Cover",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(0.65f).clip(RoundedCornerShape(16.dp))
                    )
                }
                if (useTextureView && isTextureViewOk.intValue == 0) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                TextureViewProbe.probe(this) { ok ->
                                    SharedPreferencesUtil.putInt("isTextureViewOk", if (ok) 1 else -1)
                                    isTextureViewOk.intValue = if (ok) 1 else -1
                                    if (!ok) {
                                        probeFailedThisSession.value = true
                                        showTextureDialog.value = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(1.dp)
                    )
                }

                key(useTextureView, isTextureViewOk.intValue) {
                AndroidView(
                    factory = { ctx ->
                        if (effectiveUseTexture) {
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
        }

        // DanmakuView outside scaling container to prevent scaling with video
        AndroidView(
            factory = { danmakuPlayer.view },
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
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ){
                Text(
                    text = it,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0x88000000))
                        .padding(8.dp)
                )
            }
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
                                    danmakuPlayer.pause()
                                },
                                onDragEnd = {
                                    if (dragProgress >= 0f) {
                                        val targetTime = (dragProgress * totalDuration).toLong()
                                        mediaPlayer.seekTo(targetTime)
                                        danmakuPlayer.seekTo(targetTime)
                                        currentProgress = targetTime
                                        dragProgress = -1f
                                        if (isPlaying) danmakuPlayer.resume()
                                    }
                                },
                                onDragCancel = {
                                    dragProgress = -1f
                                    if (isPlaying) danmakuPlayer.resume()
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .padding(horizontal = 36.dp)
                        .clickable { onBack() }
                        .basicMarquee()
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    val renderCustomButton = @Composable { action: Int, modifier: Modifier ->
                        when (action) {
                            1 -> {
                                IconButton(
                                    onClick = { showDanmaku = !showDanmaku },
                                    modifier = modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(if (showDanmaku) R.drawable.ic_danmaku_inline_switch_v2_on else R.drawable.ic_danmaku_inline_switch_v2_off),
                                        contentDescription = "Toggle Danmaku",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            2 -> {
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
                                        modifier = modifier.size(36.dp)
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
                            }
                            3 -> {
                                IconButton(
                                    onClick = { showVolumeScreen = true },
                                    modifier = modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.VolumeUp,
                                        contentDescription = "Volume",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (leftBtnAction != 0) {
                        renderCustomButton(leftBtnAction, Modifier.align(Alignment.CenterStart).offset(x = (-16).dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                danmakuPlayer.pause()
                                isPlaying = false
                                scope.launch {
                                    if (playerData.type == PlayerData.TYPE_LOCAL || isLive) return@launch
                                    try {
                                        val pos = mediaPlayer.currentPosition / 1000
                                        val isBangumi = playerData.type == PlayerData.TYPE_BANGUMI && playerData.epid > 0
                                        if (!isBangumi) {
                                            HistoryApi.reportHistory(playerData.aid, playerData.cid, pos)
                                        }
                                        HeartbeatApi.reportHeartbeat(
                                            aid = playerData.aid,
                                            bvid = playerData.bvid,
                                            cid = playerData.cid,
                                            playedTime = pos,
                                            type = if (isBangumi) "4" else "3",
                                            subType = if (isBangumi) "1" else null,
                                            epid = if (isBangumi) playerData.epid else null,
                                            sid = if (isBangumi) playerData.sid else null,
                                            videoDuration = (totalDuration / 1000).coerceAtLeast(0)
                                        )
                                    } catch (_: Exception) {}
                                }
                            } else {
                                mediaPlayer.start()
                                danmakuPlayer.resume()
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

                    if (rightBtnAction != 0) {
                        renderCustomButton(rightBtnAction, Modifier.align(Alignment.CenterEnd).offset(x = 16.dp))
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
        
        Dialog(
            showDialog = showVolumeScreen,
            onDismissRequest = { showVolumeScreen = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) {
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    AudioManager.ADJUST_LOWER,
                                    0
                                )
                            } else if (dragAmount < 0) {
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    AudioManager.ADJUST_RAISE,
                                    0
                                )
                            }
                        }
                    }
            ) {
                VolumeScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // TextureView 兼容性提示 dialog
        if (showTextureDialog.value && isTextureViewOk.intValue != 1) {
            val canRetry = !probeFailedThisSession.value
            WysAlertDialog(
                show = true,
                onDismissRequest = { showTextureDialog.value = false },
                title = "渲染引擎提示",
                content = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("您的设备不支持 TextureView，建议切换到 SurfaceView")
                        if (canRetry) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.wear.compose.material3.Button(onClick = {
                                showTextureDialog.value = false
                                probeFailedThisSession.value = false
                                isTextureViewOk.intValue = 0
                                SharedPreferencesUtil.putInt("isTextureViewOk", 0)
                            }) { Text("重试检测") }
                        }
                    }
                },
                onConfirm = {
                    SharedPreferencesUtil.putBoolean("player_texture_view", false)
                    showTextureDialog.value = false
                }
            )
        }
    }
}