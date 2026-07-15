package com.qx.orbit.bili.presentation.player

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

    // Dialog states
    var showVolumeScreen by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }

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
    com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen(
        mediaDisplay = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onBack()
                    }
                    .padding(horizontal = 36.dp),
            ){
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
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
                            initialDelayMillis = if (currentSubtitle != null) 0 else 2000
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentSubtitle != null) playerData.title else run {
                                val cur = currentProgress / 1000
                                val tot = totalDuration / 1000
                                "%d:%02d / %d:%02d".format(cur / 60, cur % 60, tot / 60, tot % 60)
                            },
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
            Box(modifier = Modifier.fillMaxSize()) {
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
