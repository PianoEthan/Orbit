package com.qx.orbit.bili.vision

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qx.orbit.bili.core.api.CorePlayback

@Composable
internal fun VisionLiveScreen(
    roomId: Long,
    onBack: () -> Unit,
    viewModel: VisionLiveViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val cookie = VisionUserSession.cookie
    val engine = remember { effectiveVisionEngine(VisionPreferences.playerEngine) }
    val player = remember(engine) { createVisionPlayerAdapter(context, engine) }
    var wantsPlay by remember { mutableStateOf(true) }
    var foreground by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)
    LaunchedEffect(roomId, cookie) { viewModel.open(roomId, cookie) }
    DisposableEffect(player) { onDispose { player.setListener(null); player.release() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    if (state.playback != null && playbackShouldRun(wantsPlay, true, true)) player.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        player.setListener(object : VisionPlayerListener {
            override fun onIsPlayingChanged(playing: Boolean) {}
            override fun onPlaybackStateChanged(buffering: Boolean, isEnded: Boolean) {}
            override fun onVideoSizeChanged(aspectRatio: Float) {}
            override fun onError(message: String) { playerError = message }
        })
    }

    LaunchedEffect(state.playback) {
        val playback = state.playback
        playerError = null
        if (playback == null) {
            player.pause()
            return@LaunchedEffect
        }
        player.prepare(playback, 0, 0L)
        if (playbackShouldRun(wantsPlay, foreground, true)) player.play() else player.pause()
    }
    LaunchedEffect(foreground, wantsPlay) {
        if (state.playback != null) {
            if (playbackShouldRun(wantsPlay, foreground, true)) player.play() else player.pause()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.room?.title ?: "直播间 $roomId",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    state.room?.let { room ->
                        Text(
                            buildString {
                                append(room.uname)
                                if (room.areaName.isNotBlank()) append(" · ").append(room.areaName)
                                if (room.liveStatus == 1) append(" · 人气 ${room.online}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).apply { player.bindSurfaceView(this) } },
                    modifier = Modifier.fillMaxSize(),
                )
                when (state.status) {
                    VisionLiveStatus.LOADING -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    VisionLiveStatus.LIVE -> {
                        playerError?.let {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(it, color = MaterialTheme.colorScheme.error)
                                FilledTonalButton(onClick = viewModel::retry) { Text("重新拉流") }
                            }
                        }
                    }
                    VisionLiveStatus.ENDED, VisionLiveStatus.NOT_STARTED -> {
                        LiveStatusPlaceholder(
                            title = if (state.status == VisionLiveStatus.ENDED) "主播已下播" else "主播暂未开播",
                            subtitle = if (state.status == VisionLiveStatus.ENDED) "回放功能暂未支持，可稍后回来看看" else "开播后重新进入直播间即可观看",
                        )
                    }
                    VisionLiveStatus.ERROR -> {
                        LiveStatusPlaceholder(
                            title = "无法进入直播间",
                            subtitle = state.error ?: "请稍后重试",
                            action = { Button(onClick = viewModel::retry) { Text("重试") } },
                        )
                    }
                }
            }
            val stream = state.stream
            if (state.status == VisionLiveStatus.LIVE && stream != null && stream.qualities.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("清晰度", style = MaterialTheme.typography.labelLarge)
                    stream.qualities.forEach { quality ->
                        FilterChip(
                            selected = quality.id == state.quality,
                            onClick = { viewModel.selectQuality(quality.id) },
                            label = { Text(quality.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusPlaceholder(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
