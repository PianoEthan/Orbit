package com.qx.orbit.bili.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Edit
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import coil.compose.AsyncImage
import com.qx.orbit.bili.R
import com.qx.orbit.bili.presentation.theme.BiliPink
import com.qx.orbit.bili.presentation.ui.components.DynamicCard
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.viewmodel.DynamicFeedViewModel
import kotlin.math.roundToInt
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.compose.runtime.mutableStateOf

@Composable
fun DynamicFeedScreen(
    viewModel: DynamicFeedViewModel,
    focusRequester: FocusRequester,
    navController: NavHostController,
    onTabClick: () -> Unit
) {
    val upList by viewModel.upList.collectAsState()
    val liveList by viewModel.liveList.collectAsState()
    val dynamicList by viewModel.dynamicList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showWriteDynamic by remember { mutableStateOf(false) }
    val emotes by viewModel.emotes.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedMid by viewModel.selectedMid.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    val titleHeight = 36.dp
    var actualTitleHeightPx by remember { mutableFloatStateOf(0f) }
    var titleOffset by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (actualTitleHeightPx > 0f) {
                    titleOffset = (titleOffset + delta).coerceIn(-actualTitleHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }
    
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(nestedScrollConnection)) {
        ScreenScaffold(
            scrollState = listState,
            modifier = Modifier.focusRequester(focusRequester)
        ) { contentPadding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + titleHeight,
                    bottom = contentPadding.calculateBottomPadding(),
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                
                item {
                    Button(
                        onClick = { showWriteDynamic = true },
                        modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, spec),
                        transformation = if (isRound) SurfaceTransformation(spec) else null,
                        icon = { Icon(imageVector = Icons.Filled.Edit, contentDescription = null) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("发布动态", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (liveList.isNotEmpty()) {
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .adaptiveTransformedHeight(this, spec)
                                .graphicsLayer { if (isRound) { with(spec) { applyContainerTransformation(scrollProgress) } } },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(liveList) { liveUser ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { navController.navigate("live_room/${liveUser.room_id}") }
                                ) {
                                    Box(modifier = Modifier.size(56.dp)) {
                                        UserAvatar(
                                            avatarUrl = liveUser.face,
                                            officialRole = 0,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .border(2.dp, BiliPink, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset(y = 4.dp)
                                                .background(BiliPink, RoundedCornerShape(50))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = R.drawable.ic_live_comm_live_ing,
                                                    modifier = Modifier.height(12.dp),
                                                    contentDescription = null
                                                )
                                                Text(
                                                    text = "直播中",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = liveUser.uname,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.width(56.dp).basicMarquee()
                                    )
                                }
                            }
                        }
                    }
                }

            if (upList.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            .adaptiveTransformedHeight(this, spec)
                            .graphicsLayer { if (isRound) { with(spec) { applyContainerTransformation(scrollProgress) } } },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // All dynamics option
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.selectUp(0L) }
                            ) {
                                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_all_dynamic),
                                        contentDescription = "all",
                                        modifier = Modifier
                                            .size(56.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("综合", style = MaterialTheme.typography.labelSmall, color = if (selectedMid == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        
                        items(upList) { up ->
                            val isSelected = selectedMid == up.mid
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.selectUp(up.mid) }
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    UserAvatar(
                                        avatarUrl = up.face ?: "",
                                        officialRole = 0,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    if (up.has_update) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 1.dp, y = 0.dp)
                                                .background(BiliPink, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = up.uname ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.width(56.dp).basicMarquee()
                                )
                            }
                        }
                    }
                }
            }

            if (isRefreshing) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                items(dynamicList) { dynamic ->
                    Box(modifier = Modifier.adaptiveTransformedHeight(this@items, spec)) {
                        DynamicCard(
                            item = dynamic,
                            transformation = if (isRound) SurfaceTransformation(spec) else null,
                            modifier = Modifier,
                            onUserClick = { mid -> navController.navigate("user_space/$mid") },
                            onArchiveClick = { bvid, aid ->
                                if (bvid.isNotEmpty()) {
                                    navController.navigate("detail/$bvid/$aid")
                                } else if (aid > 0) {
                                    navController.navigate("article_detail/$aid")
                                }
                            },
                            onLiveClick = { roomId ->
                                navController.navigate("live_room/$roomId")
                            },
                            onRemove = viewModel::removeDynamicLocally,
                            onTopChanged = viewModel::updateDynamicTop,
                            onReposted = viewModel::onReposted,
                            onClick = {
                                navController.navigate("dynamic_detail/${dynamic.dynamicId}")
                            }
                        )
                    }
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (errorMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { viewModel.loadMore() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.bili_2233_fail),
                                    contentDescription = "Error",
                                    modifier = Modifier.fillMaxWidth().offset(y = (-15).dp)
                                )
                                Text(
                                    text = "加载失败，点击重试",
                                    modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    item {
                        LaunchedEffect(Unit) {
                            viewModel.loadMore()
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
        }
        

        // Floating Title Area
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, titleOffset.roundToInt()) }
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                    )
                )
                .onSizeChanged { size -> 
                    actualTitleHeightPx = size.height.toFloat() 
                }
                .padding(top = 18.dp, bottom = 12.dp)
                .clickable { onTabClick() },
            contentAlignment = Alignment.TopCenter
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(1.dp))
                Text(text = "动态", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
    
    WriteReplyScreen(
        visible = showWriteDynamic,
        targetName = null,
        emotes = emotes,
        isDynamic = true,
        onSend = { text, images ->
            viewModel.publishDynamic(
                text = text,
                images = images,
                onSuccess = { showWriteDynamic = false },
                onError = { showWriteDynamic = false }
            )
        },
        onClose = { showWriteDynamic = false }
    )
}
