package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.rememberRevealState
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.viewmodel.FollowListViewModel
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun FollowListScreen(
    viewModel: FollowListViewModel,
    navController: NavController
) {
    val videoList by viewModel.videoList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var bangumiToUnfollow by remember { mutableStateOf<VideoCard?>(null) }

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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    Text("追番列表", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (errorMessage != null && videoList.isEmpty()) {
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
                            text = errorMessage ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (videoList.isEmpty() && !isLoading) {
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
                            text = "暂无追番",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(videoList, key = { _, card -> card.seasonId }) { index, videoCard ->
                // 到倒数第3条时触发加载更多
                if (index >= videoList.size - 3 && !isLoading) {
                    LaunchedEffect(index) {
                        viewModel.loadMore()
                    }
                }

                val revealState = rememberRevealState()
                LaunchedEffect(bangumiToUnfollow) {
                    if (bangumiToUnfollow == null && revealState.currentValue != RevealValue.Covered) {
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
                            onClick = { bangumiToUnfollow = videoCard },
                            icon = {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "取消追番")
                            },
                            text = { Text("取消追番") },
                            modifier = Modifier.fillMaxHeight()
                        )
                    },
                    onSwipePrimaryAction = {
                        bangumiToUnfollow = videoCard
                    }
                ) {
                    RecommendVideoCard(
                        item = videoCard,
                        onClick = {
                            navController.navigate("bangumi_detail/${videoCard.aid}")
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

            if (!videoList.isEmpty()) {
                item { Spacer(modifier = Modifier.height(30.dp)) }
            }

        }
    }

    // 缓存标题，避免弹窗关闭动画期间读到 null
    val unfollowTitle = bangumiToUnfollow?.title ?: ""

    WysAlertDialog(
        show = bangumiToUnfollow != null,
        title = "确认取消",
        content = {
            Text(
                text = "确定要取消追番《${unfollowTitle}》吗？",
                textAlign = TextAlign.Center
            )
        },
        onDismissRequest = { bangumiToUnfollow = null },
        onConfirm = {
            bangumiToUnfollow?.let {
                viewModel.unfollowBangumi(it.seasonId) { _ -> }
            }
            bangumiToUnfollow = null
        }
    )
}
