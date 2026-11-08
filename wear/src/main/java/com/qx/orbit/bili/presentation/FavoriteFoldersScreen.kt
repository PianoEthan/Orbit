package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.model.FavoriteFolder
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.WysActionMenu
import com.qx.orbit.bili.presentation.ui.components.WysActionMenuItem
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.FavoriteFolderViewModel
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun FavoriteFoldersScreen(
    viewModel: FavoriteFolderViewModel,
    navController: NavController
) {
    val folderList by viewModel.folderList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val editingFolder by viewModel.editingFolder.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val message by viewModel.message.collectAsState()
    var selectedFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<FavoriteFolder?>(null) }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            RoundToast.show(context, it)
            viewModel.clearMessage()
        }
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    
    val mid = remember {
        val midStr = CookieManager.getInfoFromCookie("DedeUserID")
        midStr.toLongOrNull() ?: 0L
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    Text("我的收藏夹", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (mid > 0L) {
                item {
                    Button(
                        onClick = viewModel::createFolder,
                        enabled = !isProcessing && !isLoading,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) {
                        Text("新建收藏夹")
                    }
                }
            }

            if (errorMessage != null && folderList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_empty22),
                            contentDescription = "Error",
                            modifier = Modifier.height(96.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (folderList.isEmpty() && !isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_empty22),
                            contentDescription = "Error",
                            modifier = Modifier.height(96.dp)
                        )
                        Text(
                            text = "暂无收藏夹",
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(folderList, key = { _, folder -> folder.id }) { index, folder ->
                val videoCard = VideoCard(
                    title = folder.name,
                    upName = "${folder.videoCount}个内容",
                    view = "",
                    cover = folder.cover,
                    type = "folder",
                    aid = folder.mediaId,
                    bvid = ""
                )
                
                SwipeToReveal(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .animateItem(),
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { if (!isProcessing) selectedFolder = folder },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            text = { Text("管理") },
                            modifier = Modifier.height(SwipeToRevealDefaults.LargeActionButtonHeight)
                        )
                    },
                    onSwipePrimaryAction = { if (!isProcessing) selectedFolder = folder }
                ) {
                    RecommendVideoCard(
                        item = videoCard,
                        onClick = { navController.navigate("favorite_detail/${folder.mediaId}/$mid") },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    )
                }
            }

            if (isLoading || isProcessing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (folderList.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    selectedFolder?.let { folder ->
        WysActionMenu(
            show = true,
            title = folder.name,
            items = buildList {
                add(WysActionMenuItem("编辑收藏夹", Icons.Default.Edit) { viewModel.editFolder(folder) })
                if (!folder.isDefault) {
                    add(WysActionMenuItem("删除收藏夹", Icons.Default.Delete, destructive = true) {
                        folderToDelete = folder
                    })
                }
            },
            onDismissRequest = { selectedFolder = null }
        )
    }

    folderToDelete?.let { folder ->
        WysAlertDialog(
            show = true,
            onDismissRequest = { folderToDelete = null },
            title = "删除收藏夹？",
            content = {
                Text(
                    "删除「${folder.name}」及其中的收藏记录，此操作无法撤销。",
                    textAlign = TextAlign.Center
                )
            },
            onConfirm = {
                folderToDelete = null
                viewModel.deleteFolder(folder)
            }
        )
    }

    editingFolder?.let { folder ->
        FavoriteFolderEditor(
            folder = folder,
            isSaving = isProcessing,
            error = actionError,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveFolder
        )
    }
}
