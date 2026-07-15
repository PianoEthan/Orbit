package com.qx.orbit.bili.presentation

import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.material3.*
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.imageLoader
import androidx.core.graphics.drawable.toBitmap
import com.qx.orbit.bili.R
import com.qx.orbit.bili.presentation.theme.extractSeedColorFromBitmap
import com.qx.orbit.bili.presentation.theme.generateWearColorSchemeFromSeed
import com.qx.orbit.bili.util.AppConfig
import com.qx.orbit.bili.util.VideoDownloadManager
import kotlinx.coroutines.delay
import java.util.Locale

data class VideoDownloadState(
    val statusText: String = "等待下载",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isDownloadFailed: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeed: Long = 0L,
    val remainingTime: Long = -1L,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false
)

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1fMB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.1fKB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return formatBytes(bytesPerSecond) + "/s"
}

private fun formatRemainingTime(seconds: Long): String {
    if (seconds < 0) return "..."
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

private fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun VideoDownloadForegroundScreen(
    navController: NavController,
    downloadId: Long
) {
    val context = LocalContext.current
    var downloadState by remember { mutableStateOf(VideoDownloadState()) }
    var title by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    val fallbackScheme = MaterialTheme.colorScheme
    var dynamicScheme by remember { mutableStateOf<ColorScheme?>(null) }
    LaunchedEffect(coverUrl) {
        if (coverUrl.isNotEmpty()) {
            val reqUrl = if (coverUrl.contains("@")) coverUrl else "$coverUrl@480w_270h_1c.webp"
            val request = ImageRequest.Builder(context)
                .data(reqUrl)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                val seed = extractSeedColorFromBitmap(bitmap)
                if (seed != null) {
                    dynamicScheme = generateWearColorSchemeFromSeed(seed, fallbackScheme)
                }
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = downloadState.progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "DownloadProgressAnimation"
    )

    // Load initial info
    LaunchedEffect(downloadId) {
        val info = VideoDownloadManager.getAllDownloads().find { it.id == downloadId }
        if (info != null) {
            title = info.title
            coverUrl = info.coverUrl
        }
    }

    // Toggle display type (speed, progress, time)
    var displayType by remember { mutableIntStateOf(0) }
    LaunchedEffect(downloadState.isDownloading) {
        while (downloadState.isDownloading) {
            delay(1500)
            displayType = (displayType + 1) % 3
        }
    }

    LaunchedEffect(downloadId) {
        var lastUpdateTime = System.currentTimeMillis()
        var lastDownloadedBytes = 0L

        while (true) {
            delay(100)
            val allDownloads = VideoDownloadManager.getAllDownloads()
            val info = allDownloads.find { it.id == downloadId }

            if (info != null) {
                val status = info.status
                val downloaded = info.downloadedBytes
                val total = info.totalBytes
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f

                when (status) {
                    DownloadManager.STATUS_PENDING -> {
                        downloadState = downloadState.copy(
                            statusText = "等待中...",
                            isDownloading = false,
                            isPaused = false,
                            isDownloadFailed = false,
                            isCompleted = false
                        )
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdateTime

                        var currentSpeed = downloadState.downloadSpeed
                        var currentRemainingTime = downloadState.remainingTime

                        if (timeDiff >= 1000) {
                            val bytesDiff = downloaded - lastDownloadedBytes
                            currentSpeed = (bytesDiff * 1000) / timeDiff
                            currentRemainingTime = if (currentSpeed > 0) (total - downloaded) / currentSpeed else -1L

                            lastUpdateTime = currentTime
                            lastDownloadedBytes = downloaded
                        }

                        downloadState = downloadState.copy(
                            statusText = "下载中",
                            progress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            downloadSpeed = currentSpeed,
                            remainingTime = currentRemainingTime,
                            isDownloading = true,
                            isPaused = false,
                            isDownloadFailed = false,
                            isCompleted = false
                        )
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloadState = downloadState.copy(
                            statusText = "已完成",
                            progress = 1f,
                            isDownloading = false,
                            isPaused = false,
                            isDownloadFailed = false,
                            isCompleted = true,
                            downloadedBytes = total,
                            totalBytes = total
                        )
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloadState = downloadState.copy(
                            statusText = "下载失败",
                            isDownloading = false,
                            isPaused = false,
                            isDownloadFailed = true,
                            isCompleted = false
                        )
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        downloadState = downloadState.copy(
                            statusText = "已暂停",
                            isDownloading = false,
                            isPaused = true,
                            isDownloadFailed = false,
                            isCompleted = false,
                            progress = progress,
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    }
                }
            } else {
                // Task removed
                navController.popBackStack()
                break
            }
        }
    }

    val currentScheme = dynamicScheme ?: fallbackScheme

    MaterialTheme(colorScheme = currentScheme) {
        ScreenScaffold {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            // A. 背景模糊 (统一视觉效果)
            if (coverUrl.isNotEmpty()) {
                val blurRadius = 100f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius.dp)
                        .alpha(0.4f)
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black
                                ),
                                center = Offset.Unspecified,
                                radius = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }

            // B. Wear Compose 进度条 (外圈)
            CircularProgressIndicator(
                progress = {
                    if (downloadState.isCompleted) 1f
                    else animatedProgress
                },
                modifier = Modifier.fillMaxSize().padding(6.dp),
                startAngle = 300f,
                endAngle = 240f,
                strokeWidth = 6.dp,
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            )

            // C. 居中内容
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.height(10.dp))
                // 2. 标题 (带跑马灯)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.ifEmpty { "未知视频" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        0.05f to Color.Black,
                                        0.95f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                // 4. 百分比或错误提示
                if (downloadState.isDownloadFailed) {
                    Text(
                        text = "下载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if ((downloadState.isDownloading || (downloadState.progress > 0f && !downloadState.isCompleted))) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = downloadState.statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (downloadState.isDownloadFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 5. 可变换的带动画文本 (速度, 时间等)
                if ((downloadState.isDownloading || downloadState.isPaused) && downloadState.totalBytes > 0) {
                    AnimatedContent(
                        targetState = displayType,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500)) { it }) togetherWith
                                    (fadeOut(animationSpec = tween(500)) + slideOutVertically(animationSpec = tween(500)) { -it })
                        },
                        label = "DownloadInfoAnimation",
                        modifier = Modifier.height(20.dp),
                        contentAlignment = Alignment.Center
                    ) { type ->
                        val text = when (type) {
                            0 -> "${formatBytes(downloadState.downloadedBytes)} / ${formatBytes(downloadState.totalBytes)}"
                            1 -> if (downloadState.isPaused) "速度: 0B/s" else formatSpeed(downloadState.downloadSpeed)
                            else -> if (downloadState.isPaused) "剩余时间: 已暂停" else "剩余时间: ${formatRemainingTime(downloadState.remainingTime)}"
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 6. 操作按钮
                if (!downloadState.isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val isRunning = downloadState.isDownloading || downloadState.statusText == "等待中..."
                    CompactButton(
                        onClick = {
                            if (isRunning) {
                                VideoDownloadManager.pause(downloadId, context)
                            } else {
                                VideoDownloadManager.resume(downloadId, context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        icon = {
                            Icon(
                                imageVector = if (downloadState.isDownloadFailed) Icons.Filled.Refresh else if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isRunning) "暂停" else "继续",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                if (downloadState.isDownloadFailed) "重试" else if (isRunning) "暂停" else "继续", 
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    CompactButton(
                        onClick = {
                            val info = VideoDownloadManager.getAllDownloads().find { it.id == downloadId }
                            if (info != null) {
                                val playerData = com.qx.orbit.bili.data.model.PlayerData(
                                    title = info.title,
                                    aid = info.aid,
                                    cid = info.cid,
                                    bvid = info.bvid,
                                    type = com.qx.orbit.bili.data.model.PlayerData.TYPE_LOCAL,
                                    videoUrl = info.localUri ?: "",
                                    audioUrl = if (info.type == "AUDIO_AND_SUBTITLE") "audio" else "",
                                    cover = info.coverUrl
                                )
                                com.qx.orbit.bili.data.api.PlayerApi.jumpToPlayer(context, navController, playerData)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "播放",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text("播放", style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }
        }
    }
}
}
