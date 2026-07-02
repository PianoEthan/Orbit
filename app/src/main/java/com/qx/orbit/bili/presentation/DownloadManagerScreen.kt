package com.qx.orbit.bili.presentation

import android.app.DownloadManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.presentation.ui.components.CacheVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.utils.VideoDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.net.URLEncoder

@Composable
fun DownloadManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var allDownloads by remember { mutableStateOf(VideoDownloadManager.getAllDownloads()) }
    var downloadToDelete by remember { mutableStateOf<Long?>(null) }
    val handleDelete: (Long) -> Unit = { id ->
        allDownloads = allDownloads.filter { it.id != id }
        coroutineScope.launch(Dispatchers.IO) {
            VideoDownloadManager.remove(context, id)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            allDownloads = VideoDownloadManager.getAllDownloads()
            delay(1000)
        }
    }

    val activeDownloads = allDownloads.filter { 
        it.status == DownloadManager.STATUS_RUNNING || 
        it.status == DownloadManager.STATUS_PENDING || 
        it.status == DownloadManager.STATUS_PAUSED ||
        it.status == DownloadManager.STATUS_FAILED 
    }
    
    val completedDownloads = allDownloads.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = context.resources.configuration.isScreenRound

    ScreenScaffold(
        scrollState = listState
    ) { it ->
        Box(modifier = Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                state = listState,
                contentPadding = it
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) {
                        Text(
                            text = "离线缓存",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.W700
                        )
                    }
                }

                if (activeDownloads.isNotEmpty()) {
                    /*item {
                        Text(
                            "当前任务",
                            modifier = Modifier
                                .graphicsLayer {
                                    if (isRound) {
                                        with(transformationSpec) {
                                            applyContainerTransformation(scrollProgress)
                                        }
                                    }
                                }
                                .transformedHeight(this, transformationSpec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    */

                    items(activeDownloads, key = { it.id }) { download ->
                        val revealState = rememberRevealState()
                        LaunchedEffect(downloadToDelete) {
                            if (downloadToDelete == null && revealState.currentValue != RevealValue.Covered) {
                                revealState.animateTo(RevealValue.Covered)
                            }
                        }
                        val progress =
                            if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes.toFloat() else 0f

                        val statusText = when (download.status) {
                            DownloadManager.STATUS_PENDING -> "等待中..."
                            DownloadManager.STATUS_RUNNING -> "正在下载 ${(progress * 100).toInt()}%"
                            DownloadManager.STATUS_PAUSED -> "已暂停"
                            DownloadManager.STATUS_FAILED -> "下载失败"
                            else -> "未知状态"
                        }

                        SwipeToReveal(
                            revealState = revealState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .transformedHeight(this, transformationSpec),
                            primaryAction = {
                                PrimaryActionButton(
                                    onClick = { downloadToDelete = download.id },
                                    icon = { Icon(Icons.Default.Close, "Cancel") },
                                    text = { Text("取消") },
                                    modifier = Modifier.fillMaxHeight()
                                )
                            },
                            onSwipePrimaryAction = { downloadToDelete = download.id }
                        ) {
                            CacheVideoCard(
                                item = download,
                                statusText = statusText,
                                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                                onClick = {
                                    if (download.status == DownloadManager.STATUS_RUNNING || download.status == DownloadManager.STATUS_PENDING) {
                                        VideoDownloadManager.pause(download.id, context)
                                    } else {
                                        VideoDownloadManager.resume(download.id, context)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )

                            if (download.status == DownloadManager.STATUS_FAILED) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, end = 8.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    CompactButton(
                                        onClick = { VideoDownloadManager.resume(download.id, context) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        icon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)) },
                                        label = { Text("重试", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (completedDownloads.isNotEmpty()) {
                    item {
                        Text(
                            "已缓存",
                            modifier = Modifier
                                .graphicsLayer {
                                    if (isRound) {
                                        with(transformationSpec) {
                                            applyContainerTransformation(scrollProgress)
                                        }
                                    }
                                }
                                .transformedHeight(this, transformationSpec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(completedDownloads, key = { it.id }) { download ->
                        val revealState = rememberRevealState()
                        LaunchedEffect(downloadToDelete) {
                            if (downloadToDelete == null && revealState.currentValue != RevealValue.Covered) {
                                revealState.animateTo(RevealValue.Covered)
                            }
                        }
                        SwipeToReveal(
                            revealState = revealState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .transformedHeight(this, transformationSpec),
                            primaryAction = {
                                PrimaryActionButton(
                                    onClick = { downloadToDelete = download.id },
                                    icon = { Icon(Icons.Default.Delete, "Delete") },
                                    text = { Text("删除") },
                                    modifier = Modifier.fillMaxHeight()
                                )
                            },
                            onSwipePrimaryAction = { downloadToDelete = download.id }
                        ) {
                            CacheVideoCard(
                                item = download,
                                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                                onClick = {
                                    val playerData = PlayerData(
                                        title = download.title,
                                        aid = download.aid,
                                        cid = download.cid,
                                        bvid = download.bvid,
                                        type = PlayerData.TYPE_LOCAL,
                                        videoUrl = download.localUri ?: "",
                                        audioUrl = if (download.type == "AUDIO_AND_SUBTITLE") "audio" else ""
                                    )
                                    val json = Gson().toJson(playerData)
                                    val encodedJson = URLEncoder.encode(json, "UTF-8")
                                    navController.navigate("player/$encodedJson")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                if (activeDownloads.isEmpty() && completedDownloads.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_empty22),
                                contentDescription = "Error",
                                modifier = Modifier.height(96.dp)
                            )
                            Text(
                                text = "暂无缓存视频",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 5.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }

        WysAlertDialog(
            show = (downloadToDelete != null),
            onDismissRequest = { downloadToDelete = null },
            title = "确认删除",
            content = { Text("确定要删除该缓存视频吗？", textAlign = TextAlign.Center) },
            onConfirm = {
                downloadToDelete?.let { handleDelete(it) }
                downloadToDelete = null
            }
        )
    }
}
