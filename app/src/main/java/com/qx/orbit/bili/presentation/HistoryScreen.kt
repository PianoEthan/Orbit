package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.qx.orbit.bili.R
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.util.navigateToVideoDetail
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.presentation.util.toPreview
import com.qx.orbit.bili.presentation.viewmodel.HistoryViewModel
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    navController: NavHostController
) {
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
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
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                ) {
                    Text("历史记录", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (items.isEmpty() && !isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_empty22),
                            contentDescription = "Empty",
                            modifier = Modifier.height(96.dp)
                        )
                        Text(
                            text = "暂无历史记录",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            itemsIndexed(
                items = items,
                key = { _, item -> item.bvid + item.cid + item.aid }
            ) { index, item ->
                SwipeToReveal(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .animateItem(),
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { viewModel.deleteHistoryItem(item.kid) },
                            icon = { Icon(Icons.Default.Delete, contentDescription = "删除") },
                            text = { Text("删除") },
                            modifier = Modifier.height(SwipeToRevealDefaults.LargeActionButtonHeight)
                        )
                    },
                    onSwipePrimaryAction = { viewModel.deleteHistoryItem(item.kid) }
                ) {
                    RecommendVideoCard(
                        item = item,
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        onClick = {
                            when (item.type) {
                                "bangumi" -> {
                                    navController.navigate("bangumi_detail/${item.seasonId}")
                                }
                                "article" -> {
                                    navController.navigate("article_detail/${item.aid}")
                                }
                                else -> {
                                    navController.navigateToVideoDetail(item.bvid, item.aid, item.toPreview())
                                }
                            }
                        }
                    )
                }

                if (index >= items.size - 3 && !isLoading) {
                    LaunchedEffect(index) {
                        viewModel.loadData()
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .adaptiveTransformedHeight(this, transformationSpec),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .height(32.dp)
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            viewModel.loadData()
        }
    }
}
