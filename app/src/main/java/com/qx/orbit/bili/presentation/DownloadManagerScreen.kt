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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.presentation.ui.components.CacheVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.VideoDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.net.URLEncoder
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation

@Composable
fun DownloadManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var allDownloads by remember { mutableStateOf(VideoDownloadManager.getAllDownloads()) }
    var downloadToDelete by remember { mutableStateOf<Long?>(null) }
    var showPlayerSelectionDialog by remember { mutableStateOf(false) }
    var selectedDownloadForPlayer by remember { mutableStateOf<VideoDownloadManager.DownloadInfo?>(null) }
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
    val isRound = LocalScreenRound.current

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
                            .adaptiveTransformedHeight(this, transformationSpec),
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
                                .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                                .adaptiveTransformedHeight(this, transformationSpec),
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
                                .adaptiveTransformedHeight(this, transformationSpec),
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
                                    navController.navigate("video_download_progress/${download.id}")
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
                    /*
                    item {
                        Text(
                            "已缓存",
                            modifier = Modifier
                                .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                                .adaptiveTransformedHeight(this, transformationSpec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }*/

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
                                .adaptiveTransformedHeight(this, transformationSpec),
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
                                        audioUrl = if (download.type == "AUDIO_AND_SUBTITLE") "audio" else "",
                                        cover = download.coverUrl
                                    )
                                    PlayerApi.jumpToPlayer(context, navController, playerData)
                                },
                                onLongClick = {
                                    selectedDownloadForPlayer = download
                                    showPlayerSelectionDialog = true
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
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
            WysAlertDialog(
                show = (downloadToDelete != null),
                onDismissRequest = { downloadToDelete = null },
                title = "确认删除",
                content = { Text("确定要删除该缓存视频吗？", textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                onConfirm = {
                    downloadToDelete?.let { handleDelete(it) }
                    downloadToDelete = null
                }
            )
            
            Dialog(
                visible = showPlayerSelectionDialog,
                onDismissRequest = { showPlayerSelectionDialog = false }
            ) {
                val dialogListState = rememberTransformingLazyColumnState()
                TransformingLazyColumn(
                    state = dialogListState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                    rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(dialogListState)
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier.adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        ) {
                            Text("选择播放器", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    val players = listOf(
                        "apsisPlayer" to "Apsis Player",
                        "pureAudioPlayer" to "Apsis AudioPlayer",
                        "aliangPlayer" to "凉腕播放器"
                    )
                    items(players.size) { index ->
                        val (playerKey, playerName) = players[index]
                        Button(
                            onClick = {
                                selectedDownloadForPlayer?.let { download ->
                                    val playerData = PlayerData(
                                        title = download.title,
                                        aid = download.aid,
                                        cid = download.cid,
                                        bvid = download.bvid,
                                        type = PlayerData.TYPE_LOCAL,
                                        videoUrl = download.localUri ?: "",
                                        audioUrl = if (download.type == "AUDIO_AND_SUBTITLE") "audio" else "",
                                        cover = download.coverUrl
                                    )
                                    PlayerApi.jumpToPlayer(context, navController, playerData, playerKey)
                                }
                                showPlayerSelectionDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                            transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(playerName)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
