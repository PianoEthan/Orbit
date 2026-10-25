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
import androidx.compose.ui.text.style.TextOverflow
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
    var groupToDelete by remember { mutableStateOf<CachedGroup?>(null) }
    var selectedGroupForEpisodes by remember { mutableStateOf<CachedGroup?>(null) }
    var showPlayerSelectionDialog by remember { mutableStateOf(false) }
    var selectedDownloadForPlayer by remember { mutableStateOf<VideoDownloadManager.DownloadInfo?>(null) }
    var selectedGroupForPlayer by remember { mutableStateOf<CachedGroup?>(null) }
    var selectedEpisodeIndexForPlayer by remember { mutableIntStateOf(0) }

    val playCachedEpisode: (VideoDownloadManager.DownloadInfo, List<VideoDownloadManager.DownloadInfo>, Int, String?) -> Unit = { download, allInGroup, index, playerKey ->
        val pagenames = allInGroup.map { d ->
            if (d.title.contains(" - ")) d.title.substringAfter(" - ").trim() else d.title
        }
        val cids = allInGroup.map { it.cid }
        val aids = allInGroup.map { it.aid }
        val localUrls = allInGroup.map { it.localUri ?: "" }
        val playerData = PlayerData(
            title = download.title,
            aid = download.aid,
            cid = download.cid,
            bvid = download.bvid,
            type = PlayerData.TYPE_LOCAL,
            videoUrl = download.localUri ?: "",
            audioUrl = if (download.type == "AUDIO_AND_SUBTITLE") "audio" else "",
            cover = download.coverUrl,
            pagenames = pagenames,
            cids = cids,
            aids = aids,
            localUrls = localUrls,
            currentPageIndex = index
        )
        PlayerApi.jumpToPlayer(context, navController, playerData, playerKey)
    }

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

    val groupedCompleted = remember(completedDownloads) {
        val map = linkedMapOf<String, MutableList<VideoDownloadManager.DownloadInfo>>()
        for (d in completedDownloads) {
            val groupKey = when {
                d.bvid.isNotBlank() -> "bvid_${d.bvid}"
                d.aid > 0 -> "aid_${d.aid}"
                d.title.contains(" - ") -> "title_${d.title.substringBefore(" - ").trim()}"
                else -> "id_${d.id}"
            }
            map.getOrPut(groupKey) { mutableListOf() }.add(d)
        }
        map.map { (key, list) ->
            val sortedList = list.sortedWith(compareBy({ extractPartNumber(it.title) }, { it.id }))
            val first = sortedList.first()
            val commonTitle = if (sortedList.size > 1 && first.title.contains(" - ")) {
                first.title.substringBefore(" - ").trim()
            } else {
                first.title
            }
            CachedGroup(
                key = key,
                title = commonTitle,
                coverUrl = sortedList.firstOrNull { it.coverUrl.isNotEmpty() }?.coverUrl ?: "",
                downloads = sortedList
            )
        }
    }

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

                    items(groupedCompleted, key = { it.key }) { group ->
                        val revealState = rememberRevealState()
                        LaunchedEffect(downloadToDelete, groupToDelete) {
                            if (downloadToDelete == null && groupToDelete == null && revealState.currentValue != RevealValue.Covered) {
                                revealState.animateTo(RevealValue.Covered)
                            }
                        }
                        val isMulti = group.downloads.size > 1
                        SwipeToReveal(
                            revealState = revealState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .adaptiveTransformedHeight(this, transformationSpec),
                            primaryAction = {
                                PrimaryActionButton(
                                    onClick = {
                                        if (isMulti) {
                                            groupToDelete = group
                                        } else {
                                            downloadToDelete = group.downloads.first().id
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Delete, "Delete") },
                                    text = { Text(if (isMulti) "删除全部" else "删除") },
                                    modifier = Modifier.fillMaxHeight()
                                )
                            },
                            onSwipePrimaryAction = {
                                if (isMulti) {
                                    groupToDelete = group
                                } else {
                                    downloadToDelete = group.downloads.first().id
                                }
                            }
                        ) {
                            val displayItem = remember(group) {
                                if (isMulti) {
                                    group.downloads.first().copy(title = group.title)
                                } else {
                                    group.downloads.first()
                                }
                            }
                            CacheVideoCard(
                                item = displayItem,
                                statusText = if (isMulti) "共 ${group.downloads.size} 集" else null,
                                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                                onClick = {
                                    if (isMulti) {
                                        selectedGroupForEpisodes = group
                                    } else {
                                        playCachedEpisode(group.downloads.first(), group.downloads, 0, null)
                                    }
                                },
                                onLongClick = {
                                    if (isMulti) {
                                        selectedGroupForEpisodes = group
                                    } else {
                                        selectedDownloadForPlayer = group.downloads.first()
                                        selectedGroupForPlayer = group
                                        selectedEpisodeIndexForPlayer = 0
                                        showPlayerSelectionDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                if (activeDownloads.isEmpty() && groupedCompleted.isEmpty()) {
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
                content = { Text("确定要删除该缓存视频吗？", textAlign = TextAlign.Center) },
                onConfirm = {
                    val id = downloadToDelete
                    if (id != null) {
                        handleDelete(id)
                        selectedGroupForEpisodes?.let { g ->
                            val remaining = g.downloads.filter { it.id != id }
                            if (remaining.isEmpty()) {
                                selectedGroupForEpisodes = null
                            } else {
                                selectedGroupForEpisodes = g.copy(downloads = remaining)
                            }
                        }
                    }
                    downloadToDelete = null
                }
            )

            WysAlertDialog(
                show = (groupToDelete != null),
                onDismissRequest = { groupToDelete = null },
                title = "确认删除",
                content = {
                    Text(
                        "确定要删除该视频的全部 ${groupToDelete?.downloads?.size ?: 0} 个缓存吗？",
                        textAlign = TextAlign.Center
                    )
                },
                onConfirm = {
                    val targetGroup = groupToDelete
                    targetGroup?.downloads?.forEach { d ->
                        handleDelete(d.id)
                    }
                    if (selectedGroupForEpisodes?.key == targetGroup?.key) {
                        selectedGroupForEpisodes = null
                    }
                    groupToDelete = null
                }
            )

            Dialog(
                visible = (selectedGroupForEpisodes != null),
                onDismissRequest = { selectedGroupForEpisodes = null }
            ) {
                val group = selectedGroupForEpisodes ?: return@Dialog
                val epDialogListState = rememberTransformingLazyColumnState()
                TransformingLazyColumn(
                    state = epDialogListState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(epDialogListState)
                ) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        ListHeader {
                            Text(
                                text = "选集 (共${group.downloads.size}集)",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(group.downloads.size, key = { group.downloads[it].id }) { index ->
                        val download = group.downloads[index]
                        val epTitle = if (download.title.contains(" - ")) {
                            download.title.substringAfter(" - ").trim()
                        } else {
                            download.title
                        }
                        val isAudio = download.type == "AUDIO_AND_SUBTITLE"
                        val typeTag = if (isAudio) " [音频]" else ""
                        val displayText = if (epTitle.matches(Regex("^P\\d+.*"))) epTitle else "P${index + 1} $epTitle"

                        val epRevealState = rememberRevealState()
                        SwipeToReveal(
                            revealState = epRevealState,
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
                            Button(
                                onClick = {
                                    playCachedEpisode(download, group.downloads, index, null)
                                    selectedGroupForEpisodes = null
                                },
                                onLongClick = {
                                    selectedDownloadForPlayer = download
                                    selectedGroupForPlayer = group
                                    selectedEpisodeIndexForPlayer = index
                                    showPlayerSelectionDialog = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .adaptiveTransformedHeight(this, transformationSpec)
                            ) {
                                Text(
                                    text = "$displayText$typeTag",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
            
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
                                    val allInGroup = selectedGroupForPlayer?.downloads ?: listOf(download)
                                    val epIdx = selectedEpisodeIndexForPlayer
                                    playCachedEpisode(download, allInGroup, epIdx, playerKey)
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

private data class CachedGroup(
    val key: String,
    val title: String,
    val coverUrl: String,
    val downloads: List<VideoDownloadManager.DownloadInfo>
)

private fun extractPartNumber(title: String): Int {
    val match = Regex("""(?i)[Pp](\d+)""").find(title)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
