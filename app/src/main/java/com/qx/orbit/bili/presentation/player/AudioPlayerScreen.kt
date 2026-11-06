package com.qx.orbit.bili.presentation.player

import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalRippleConfiguration
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.material3.VolumeScreen
import com.google.android.horologist.media.ui.material3.components.animated.AnimatedMediaControlButtons
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.theme.extractSeedColorFromBitmap
import com.qx.orbit.bili.presentation.theme.generateWearColorSchemeFromSeed
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.PlayerViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.player.OrbitPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val AUDIO_HOLD_SEEK_ENABLED_KEY = "player_audio_longpress_seek"
private const val AUDIO_HOLD_SEEK_HINT_KEY = "player_audio_longpress_seek_hint"

private fun formatAudioTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun AudioPlayerScreen(
    viewModel: PlayerViewModel,
    playerData: PlayerData,
    mediaPlayer: OrbitPlayer,
    audioManager: AudioManager,
    isPlaying: Boolean,
    currentProgress: Long,
    totalDuration: Long,
    currentSubtitle: String?,
    currentSubtitleId: Long,
    playbackSpeed: Float,
    subtitleLinks: Array<com.qx.orbit.bili.data.model.SubtitleLink>,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var lastTitleClickTime by remember { mutableStateOf(0L) }

    // Dialog states
    var showVolumeScreen by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }

    // 长按快进/快退：按住期间只预览目标位置，松手时一次性 seek（避免反复 seek 造成卡顿/发热）
    var holdSeekEnabled by remember { mutableStateOf(SharedPreferencesUtil.getBoolean(AUDIO_HOLD_SEEK_ENABLED_KEY, true)) }
    var holdAnchorMs by remember { mutableLongStateOf(0L) }
    var holdStartedAtMs by remember { mutableLongStateOf(0L) }
    var holdRewind by remember { mutableStateOf(false) }
    val holdActive = holdStartedAtMs > 0L
    val canHoldSeek = holdSeekEnabled && !viewModel.isLive && totalDuration > 0L
    val hapticFeedback = LocalHapticFeedback.current

    // 纯手势功能需要一次发现提示，否则用户永远不知道可以长按
    LaunchedEffect(Unit) {
        if (holdSeekEnabled && !SharedPreferencesUtil.getBoolean(AUDIO_HOLD_SEEK_HINT_KEY, false)) {
            SharedPreferencesUtil.putBoolean(AUDIO_HOLD_SEEK_HINT_KEY, true)
            RoundToast.show(context, "长按左右两侧可快退/快进")
        }
    }

    fun startHoldSeek(rewind: Boolean) {
        holdAnchorMs = currentProgress.coerceIn(0L, totalDuration)
        holdRewind = rewind
        holdStartedAtMs = SystemClock.elapsedRealtime()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun cancelHoldSeek() {
        holdStartedAtMs = 0L
    }

    fun commitHoldSeek() {
        val startedAt = holdStartedAtMs
        val rewind = holdRewind
        holdStartedAtMs = 0L
        if (startedAt <= 0L) return
        val heldMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val offsetMs = audioHoldSeekOffsetMs(heldMs)
        viewModel.seekTo(
            audioHoldSeekTargetMs(holdAnchorMs, if (rewind) -offsetMs else offsetMs, totalDuration)
        )
    }

    val holdCallbacks = AudioHoldSeekCallbacks(
        enabled = canHoldSeek,
        onTap = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTitleClickTime < 2000) {
                onBack()
            } else {
                lastTitleClickTime = currentTime
                RoundToast.show(context, "再次点击退出播放")
            }
        },
        onHoldStart = { rewind -> startHoldSeek(rewind) },
        onHoldCancel = { cancelHoldSeek() },
        onHoldCommit = { commitHoldSeek() }
    )

    // Extract theme color from cover image
    var audioColorScheme by remember { mutableStateOf<androidx.wear.compose.material3.ColorScheme?>(null) }
    val defaultColorScheme = MaterialTheme.colorScheme
    LaunchedEffect(playerData.cover) {
        val rawCover = playerData.cover
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
                        audioColorScheme = generateWearColorSchemeFromSeed(seedColor, defaultColorScheme)
                    }
                }
            }
        }
    }

    MaterialTheme(colorScheme = audioColorScheme ?: defaultColorScheme) {
    Box(modifier = Modifier.fillMaxSize()) {
    com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen(
        mediaDisplay = {
            // 铺满顶部整块区域，长按快进的命中范围就不只是标题条
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .audioHoldSeekGestures(holdCallbacks),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.1f to Color.Black,
                                    0.9f to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = currentSubtitle ?: playerData.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = if (currentSubtitle != null) 0 else 2000,
                            velocity = if (currentSubtitle != null) 80.dp else 30.dp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentSubtitle != null) playerData.title else
                                "${formatAudioTime(currentProgress)} / ${formatAudioTime(totalDuration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        controlButtons = {
            val trackPosition = TrackPositionUiModel.Actual(
                percent = if (totalDuration > 0) currentProgress.toFloat() / totalDuration else 0f,
                duration = totalDuration.milliseconds,
                position = currentProgress.milliseconds
            )
            @OptIn(ExperimentalMaterial3Api::class)
            CompositionLocalProvider(
                LocalRippleConfiguration provides null
            ) {
                AnimatedMediaControlButtons(
                    onPlayButtonClick = { viewModel.play() },
                    onPauseButtonClick = { viewModel.pause() },
                    onSeekToNextButtonClick = {
                        val newProgress = (currentProgress + 10000).coerceAtMost(totalDuration)
                        viewModel.seekTo(newProgress)
                        if (isPlaying) viewModel.play()
                    },
                    seekToNextButtonEnabled = true,
                    onSeekToPreviousButtonClick = {
                        val newProgress = (currentProgress - 10000).coerceAtLeast(0)
                        viewModel.seekTo(newProgress)
                        if (isPlaying) viewModel.play()
                    },
                    seekToPreviousButtonEnabled = true,
                    playing = isPlaying,
                    playPauseButtonEnabled = true,
                    trackPositionUiModel = trackPosition
                )
            }
        },
        buttons = {
            ButtonGroup(
                modifier = Modifier
                    .padding(
                        top = 8.dp,
                        start = 28.dp,
                        end = 28.dp,
                        bottom = 28.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                spacing = 4.dp
            ) {
                val volumeInteractionSource =
                    remember { MutableInteractionSource() }
                // 0. Volume button
                FilledIconButton(
                    onClick = { showVolumeScreen = true },
                    modifier = Modifier
                        .animateWidth(volumeInteractionSource),
                    interactionSource = volumeInteractionSource,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "音量/设备",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 1. Subtitle button
                val subtitleInteractionSource =
                    remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = { showSubtitleDialog = true },
                    modifier = Modifier
                        .animateWidth(subtitleInteractionSource),
                    interactionSource = subtitleInteractionSource,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = "字幕",
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 2. More button
                val moreInteractionSource = remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = { showMoreDialog = true },
                    modifier = Modifier
                        .animateWidth(moreInteractionSource),
                    interactionSource = moreInteractionSource,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        background = {
            // 背景层在按钮之下：按钮自己的点击优先，其余空白处都能长按
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .audioHoldSeekGestures(holdCallbacks.copy(onTap = null))
            ) {
                AsyncImage(
                    model = playerData.cover.replace("http://", "https://"),
                    contentDescription = "Background Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 24.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black
                                ),
                                center = Offset.Unspecified,
                                radius = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }
        }
    )

    if (holdActive) {
        AudioHoldSeekOverlay(
            startedAtMs = holdStartedAtMs,
            anchorMs = holdAnchorMs,
            durationMs = totalDuration,
            rewind = holdRewind
        )
    }
    } // Box

    // --- Dialogs ---
    Dialog(
        visible = showVolumeScreen,
        onDismissRequest = { showVolumeScreen = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                        } else if (dragAmount < 0) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                        }
                    }
                }
        ) {
            VolumeScreen(
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Dialog(
        visible = showSubtitleDialog,
        onDismissRequest = { showSubtitleDialog = false }
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val isRound = LocalScreenRound.current

        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                ListHeader {
                    Text(text = "选择字幕", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            item {
                val isSelected = currentSubtitleId == 0L
                Button(
                    onClick = {
                        viewModel.setSubtitles(emptyArray())
                        viewModel.setCurrentSubtitleId(0L)
                        showSubtitleDialog = false
                    },
                    colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    icon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "关闭字幕")
                }
            }
            itemsIndexed(subtitleLinks.toList()) { _, link ->
                val isSelected = currentSubtitleId == link.id
                Button(
                    onClick = {
                        if (link.url.isEmpty()) return@Button
                        scope.launch {
                            try {
                                val subs = if (link.url.startsWith("/") && !link.url.startsWith("//")) {
                                    viewModel.loadSubtitleFromPath(link.url)
                                } else {
                                    PlayerApi.getSubtitle(link.url)
                                }
                                viewModel.setSubtitles(subs)
                                viewModel.setCurrentSubtitleId(link.id)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "加载字幕失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            showSubtitleDialog = false
                        }
                    },
                    colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    icon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = link.lang)
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    Dialog(
        visible = showMoreDialog,
        onDismissRequest = { showMoreDialog = false }
    ) {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()
        val isRound = LocalScreenRound.current

        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                ListHeader {
                    Text(text = "更多选项", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Button(
                    onClick = {
                        val newSpeed = when (playbackSpeed) {
                            1.0f -> 1.5f; 1.5f -> 2.0f; 2.0f -> 0.5f; else -> 1.0f
                        }
                        viewModel.setSpeed(newSpeed)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "倍速: ${playbackSpeed}x")
                }
            }
            item {
                Button(
                    onClick = {
                        holdSeekEnabled = !holdSeekEnabled
                        SharedPreferencesUtil.putBoolean(AUDIO_HOLD_SEEK_ENABLED_KEY, holdSeekEnabled)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = if (holdSeekEnabled) "长按快进/快退: 开" else "长按快进/快退: 关")
                }
            }
            item {
                val isLooping = remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_loop", false)) }
                Button(
                    onClick = {
                        isLooping.value = !isLooping.value
                        SharedPreferencesUtil.putBoolean("player_loop", isLooping.value)
                        mediaPlayer.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "loop", if (isLooping.value) 1 else 0)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = if (isLooping.value) "循环播放: 开" else "循环播放: 关")
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    } // MaterialTheme
}

/** 按住快进/快退时的遮罩预览：只显示目标位置，松手才真正 seek。 */
@Composable
private fun AudioHoldSeekOverlay(
    startedAtMs: Long,
    anchorMs: Long,
    durationMs: Long,
    rewind: Boolean
) {
    var elapsedMs by remember(startedAtMs) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            elapsedMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            delay(60L)
        }
    }
    val offsetMs = audioHoldSeekOffsetMs(elapsedMs)
    val targetMs = audioHoldSeekTargetMs(anchorMs, if (rewind) -offsetMs else offsetMs, durationMs)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (rewind) "快退 -${formatAudioTime(offsetMs)}" else "快进 +${formatAudioTime(offsetMs)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatAudioTime(targetMs)} / ${formatAudioTime(durationMs)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
