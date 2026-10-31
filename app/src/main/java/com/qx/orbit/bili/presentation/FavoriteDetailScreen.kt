package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.util.navigateToVideoDetail
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.util.toPreview
import com.qx.orbit.bili.presentation.viewmodel.FavoriteDetailViewModel
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation

@Composable
fun FavoriteDetailScreen(
    viewModel: FavoriteDetailViewModel,
    navController: NavHostController,
    fid: Long
) {
    LaunchedEffect(fid) {
        viewModel.initLoad(fid)
    }

    val videoList by viewModel.videoList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var videoToDelete by remember { mutableStateOf<VideoCard?>(null) }
    var searchText by remember { mutableStateOf("") }
    val isRound = LocalScreenRound.current

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
            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("搜索收藏夹", color = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(50.dp),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.search(searchText) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search(searchText) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.background,
                            unfocusedBorderColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }

            if (errorMessage != null && videoList.isEmpty()) {
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
            } else if (videoList.isEmpty() && !isLoading) {
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
                            text = "暂无内容",
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(videoList, key = { _, card -> card.aid }) { index, videoCard ->
                if (index >= videoList.size - 3 && !isLoading) {
                    LaunchedEffect(index) {
                        viewModel.loadMore()
                    }
                }

                val revealState = rememberRevealState()
                LaunchedEffect(videoToDelete) {
                    if (videoToDelete == null && revealState.currentValue != RevealValue.Covered) {
                        revealState.animateTo(RevealValue.Covered)
                    }
                }

                SwipeToReveal(
                    revealState = revealState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .animateItem(),
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { videoToDelete = videoCard },
                            icon = { Icon(Icons.Default.Delete, contentDescription = "取消收藏") },
                            text = { Text("取消收藏") },
                            modifier = Modifier.fillMaxHeight()
                        )
                    },
                    onSwipePrimaryAction = { videoToDelete = videoCard }
                ) {
                    RecommendVideoCard(
                        item = videoCard,
                        onClick = {
                            navController.navigateToVideoDetail(videoCard.bvid, videoCard.aid, videoCard.toPreview())
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (videoList.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    val deleteTitle = videoToDelete?.title ?: ""
    WysAlertDialog(
        show = videoToDelete != null,
        title = "确认取消",
        content = {
            Text(
                text = "确定要取消收藏《${deleteTitle}》吗？",
                textAlign = TextAlign.Center
            )
        },
        onDismissRequest = { videoToDelete = null },
        onConfirm = {
            videoToDelete?.let {
                viewModel.deleteVideo(it.aid) { _ -> }
            }
            videoToDelete = null
        }
    )
}
