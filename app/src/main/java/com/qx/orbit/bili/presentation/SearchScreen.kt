package com.qx.orbit.bili.presentation
import com.qx.orbit.bili.presentation.ui.components.WysTimeText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Job
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlin.math.roundToInt
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import androidx.wear.compose.material3.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text as Material3Text
import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.viewmodel.SearchTab
import com.qx.orbit.bili.presentation.viewmodel.SearchViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.data.api.BilibiliIDConverter
import com.qx.orbit.bili.util.formatCount
import kotlinx.coroutines.launch

object SearchHistoryManager {
    private const val KEY = "search_history"
    fun getHistory(): List<String> {
        val str = SharedPreferencesUtil.getString(KEY, "")
        return if (str.isEmpty()) emptyList() else str.split("||")
    }
    fun addHistory(query: String) {
        val list = getHistory().toMutableList()
        list.remove(query)
        list.add(0, query)
        if (list.size > 20) list.removeAt(list.lastIndex)
        SharedPreferencesUtil.putString(KEY, list.joinToString("||"))
    }
    fun removeHistory(query: String) {
        val list = getHistory().toMutableList()
        list.remove(query)
        SharedPreferencesUtil.putString(KEY, list.joinToString("||"))
    }
}

@Composable
fun SearchInputScreen(navController: NavHostController) {
    var searchText by remember { mutableStateOf("") }
    var historyList by remember { mutableStateOf(SearchHistoryManager.getHistory()) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = androidx.compose.ui.platform.LocalConfiguration.current.isScreenRound

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val performSearch = { query: String ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            SearchHistoryManager.addHistory(trimmed)
            historyList = SearchHistoryManager.getHistory()
            if (trimmed.matches(Regex("(?i)^(av\\d+|bv[0-9a-zA-Z]{10})$"))) {
                val isBv = trimmed.lowercase().startsWith("bv")
                val bvid = if (isBv) trimmed else BilibiliIDConverter.aidToBv(trimmed.lowercase().removePrefix("av").toLong())
                val aid = if (!isBv) trimmed.lowercase().removePrefix("av").toLong() else BilibiliIDConverter.bvToAid(trimmed)
                navController.navigate("detail/$bvid/$aid")
            } else {
                val encodedQuery = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
                navController.navigate("search_result/$encodedQuery")
            }
        }
    }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = {
                            Material3Text("搜你所想", color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.background,
                            unfocusedBorderColor = MaterialTheme.colorScheme.background
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(searchText) }),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { performSearch(searchText) },
                        modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize),
                        enabled = searchText.isNotBlank(),
                        shapes = IconButtonDefaults.animatedShapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            }
            
            if (historyList.isNotEmpty()) {
                item {
                    Text(
                        text = "搜索历史",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                    )
                }
                items(historyList, key = { it }) { history ->
                    SwipeToReveal(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this,transformationSpec)
                            .animateItem(),

                        primaryAction = {
                            PrimaryActionButton(
                                onClick = {
                                    SearchHistoryManager.removeHistory(history)
                                    historyList = SearchHistoryManager.getHistory()
                                },
                                icon = {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "删除")
                                },
                                text = { Text("删除") },
                                modifier = Modifier.height(SwipeToRevealDefaults.LargeActionButtonHeight)
                            )
                        },
                        onSwipePrimaryAction = {
                            SearchHistoryManager.removeHistory(history)
                            historyList = SearchHistoryManager.getHistory()
                        }
                    ) {
                        Button(
                            onClick = { performSearch(history) },
                            modifier = Modifier.fillMaxWidth(),
                            transformation = SurfaceTransformation(transformationSpec),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Text(text = history, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultScreen(viewModel: SearchViewModel, query: String, navController: NavHostController) {
    val currentTab by viewModel.currentTab.collectAsState()
    val resultsMap by viewModel.results.collectAsState()
    val isLoadingMap by viewModel.isLoading.collectAsState()
    val errorMessageMap by viewModel.errorMessage.collectAsState()
    var showTabMenu by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = SearchTab.entries.indexOf(currentTab),
        pageCount = { SearchTab.entries.size }
    )

    LaunchedEffect(query) {
        viewModel.performSearch(query)
    }

    LaunchedEffect(pagerState.settledPage) {
        val tab = SearchTab.entries[pagerState.settledPage]
        viewModel.switchTab(tab)
    }

    LaunchedEffect(currentTab) {
        val tabIndex = SearchTab.entries.indexOf(currentTab)
        if (pagerState.currentPage != tabIndex) {
            pagerState.animateScrollToPage(tabIndex)
        }
    }

    val focusRequesters = remember { List(SearchTab.entries.size) { FocusRequester() } }
    LaunchedEffect(showTabMenu, currentTab) {
        if (!showTabMenu) {
            val tabIndex = SearchTab.entries.indexOf(currentTab)
            try { focusRequesters[tabIndex].requestFocus() } catch (e: Exception) {}
        }
    }

    var actualTitleHeightPx by remember { mutableFloatStateOf(0f) }
    val titleOffsetAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(pagerState) {
        var animJob: Job? = null
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
            .collect { (inProgress, _) ->
                if (inProgress || titleOffsetAnim.value != 0f) {
                    animJob?.cancel()
                    animJob = launch {
                        if (titleOffsetAnim.value != 0f) {
                            titleOffsetAnim.animateTo(0f, animationSpec = tween(300))
                        }
                    }
                }
            }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pagerState.isScrollInProgress) return Offset.Zero
                
                if (titleOffsetAnim.isRunning && titleOffsetAnim.targetValue == 0f) {
                    return Offset.Zero
                }

                val delta = available.y
                if (actualTitleHeightPx > 0f) {
                    val newVal = (titleOffsetAnim.value + delta).coerceIn(-actualTitleHeightPx, 0f)
                    coroutineScope.launch {
                        titleOffsetAnim.snapTo(newVal)
                    }
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isFocusedPage = pagerState.currentPage == page
            LaunchedEffect(isFocusedPage) {
                if (isFocusedPage) {
                    try { focusRequesters[page].requestFocus() } catch (e: Exception) {}
                }
            }
            
            val tab = SearchTab.entries[page]
            val results = resultsMap[tab] ?: emptyList()
            val isLoading = isLoadingMap[tab] ?: false
            val errorMessage = errorMessageMap[tab]

            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(
                timeText = { WysTimeText() },
                scrollState = listState,
                modifier = Modifier.fillMaxSize().focusRequester(focusRequesters[page])
            ) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize()
                , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                    item {
                        Spacer(modifier = Modifier.height(36.dp))
                    }

                    itemsIndexed(results) { index, item ->
                        if (index >= results.size - 3 && !isLoading) {
                            LaunchedEffect(index) {
                                viewModel.loadMore(tab)
                            }
                        }

                        // Reduced gap
                        Box(modifier = Modifier.transformedHeight(this@itemsIndexed, transformationSpec)) {
                            when (item) {
                                is VideoCard -> RecommendVideoCard(
                                    item = item, 
                                    onClick = {
                                        if (item.type == "bangumi") {
                                            navController.navigate("bangumi_detail/${item.aid}")
                                        } else if (item.bvid.isNotEmpty() || item.aid > 0) {
                                            navController.navigate("detail/${item.bvid}/${item.aid}")
                                        }
                                    },
                                    transformation = SurfaceTransformation(transformationSpec)
                                )
                                is LiveRoom -> LiveRoomCard(
                                    item = item, 
                                    onClick = {
                                        navController.navigate("live_room/${item.roomid}")
                                    },
                                    transformation = SurfaceTransformation(transformationSpec)
                                )
                                is UserInfo -> UserInfoCard(
                                    item = item, 
                                    onClick = {
                                        navController.navigate("user_space/${item.mid}")
                                    },
                                    transformation = SurfaceTransformation(transformationSpec)
                                )
                                is ArticleCard -> ArticleCardItem(
                                    item = item, 
                                    onClick = {
                                        navController.navigate("article_detail/${item.id}")
                                    },
                                    transformation = SurfaceTransformation(transformationSpec)
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    if (errorMessage != null && results.isEmpty()) {
                        item {
                            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        }
                    }
                    
                    if (results.isEmpty() && !isLoading) {
                        item {
                            Text(text = "没有找到相关结果", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        
        // Floating Menu Button
        val localDensity = LocalDensity.current
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, titleOffsetAnim.value.roundToInt()) }
                .padding(top = 22.dp)
                .zIndex(2f)
                .onGloballyPositioned { coordinates ->
                    actualTitleHeightPx = coordinates.size.height.toFloat() + with(localDensity) { 24.dp.toPx() }
                }
        ) {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { 
                        if (showTabMenu) {
                            showTabMenu = false
                        } else {
                            showTabMenu = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentDisplayTab = SearchTab.entries.getOrNull(pagerState.currentPage) ?: currentTab
                    if (showTabMenu) {
                        Text(
                            text = "返回",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "返回",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = currentDisplayTab.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(1.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "切换",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Tab Menu Overlay
        AnimatedVisibility(
            visible = showTabMenu,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val menuListState = rememberTransformingLazyColumnState()
                val menuFocusRequester = remember { FocusRequester() }
                val menuTransformationSpec = rememberTransformationSpec()

                LaunchedEffect(showTabMenu) {
                    if (showTabMenu) {
                        try { menuFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }

                ScreenScaffold(
                    timeText = { WysTimeText() },
                    scrollState = menuListState,
                    modifier = Modifier.focusRequester(menuFocusRequester)
                ) { contentPadding ->
                    TransformingLazyColumn(
                        state = menuListState,
                        contentPadding = contentPadding,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(menuListState)) {
                        item {
                            Spacer(modifier = Modifier.height(36.dp))
                        }
                        
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, menuTransformationSpec)
                                    .graphicsLayer {
                                        with(menuTransformationSpec) {
                                            applyContainerTransformation(scrollProgress)
                                        }
                                    }
                            ) {
                                var innerSearch by remember(query) { mutableStateOf(query) }
                                val doInnerSearch = {
                                    val trimmed = innerSearch.trim()
                                    if (trimmed.isNotBlank() && trimmed != query) {
                                        SearchHistoryManager.addHistory(trimmed)
                                        if (trimmed.matches(Regex("(?i)^(av\\d+|bv[0-9a-zA-Z]{10})$"))) {
                                            val isBv = trimmed.lowercase().startsWith("bv")
                                            val bvid = if (isBv) trimmed else BilibiliIDConverter.aidToBv(trimmed.lowercase().removePrefix("av").toLong())
                                            val aid = if (!isBv) trimmed.lowercase().removePrefix("av").toLong() else BilibiliIDConverter.bvToAid(trimmed)
                                            navController.navigate("detail/$bvid/$aid")
                                        } else {
                                            val encodedQuery = java.net.URLEncoder.encode(trimmed, java.nio.charset.StandardCharsets.UTF_8.toString())
                                            navController.navigate("search_result/$encodedQuery") {
                                                popUpTo("search") { inclusive = false }
                                            }
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = innerSearch,
                                        onValueChange = { innerSearch = it },
                                        singleLine = true,
                                        shape = RoundedCornerShape(50.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            focusedBorderColor = MaterialTheme.colorScheme.background,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.background
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { doInnerSearch() }),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilledIconButton(
                                        onClick = { doInnerSearch() },
                                        shapes = IconButtonDefaults.animatedShapes(),
                                        modifier = Modifier.size(IconButtonDefaults.DefaultButtonSize),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
                                    }
                                }
                            }
                        }

                        items(SearchTab.entries.size) { index ->
                            val tab = SearchTab.entries[index]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, menuTransformationSpec)
                                    .graphicsLayer {
                                        with(menuTransformationSpec) {
                                            applyContainerTransformation(scrollProgress)
                                        }
                                    }
                            ) {
                                val isSelected = currentTab == tab
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape)
                                        .height(48.dp)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                        .clickable {
                                            viewModel.switchTab(tab)
                                            showTabMenu = false
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = tab.title,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveRoomCard(
    item: LiveRoom, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    val coverUrl = item.user_cover.ifEmpty { item.cover }.ifEmpty { item.keyframe }
    RecommendVideoCard(
        item = VideoCard(
            title = item.title,
            cover = coverUrl,
            upName = item.uname,
            view = formatCount(item.online)
        ),
        onClick = onClick,
        modifier = modifier,
        transformation = transformation
    )
}

@Composable
fun UserInfoCard(
    item: UserInfo, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    Button(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth(), 
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        icon = {
            UserAvatar(
                avatarUrl = item.avatar,
                officialRole = item.official,
                modifier = Modifier.size(36.dp),
                isVip = item.vip_role > 0
            )
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserNameText(
                    name = item.name,
                    isVip = item.vip_role > 0,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.basicMarquee().weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Spacer(modifier = Modifier.width(4.dp))
                com.qx.orbit.bili.presentation.ui.components.LevelIcon(
                    level = item.level,
                    isSenior = item.is_senior_member == 1
                )
            }
        },
        secondaryLabel = {
            Text(
                text = "${formatCount(item.fans)}粉丝",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    )
}

@Composable
fun ArticleCardItem(
    item: ArticleCard, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    RecommendVideoCard(
        item = VideoCard(
            title = item.title,
            cover = item.cover,
            upName = item.upName,
            view = item.view
        ),
        onClick = onClick,
        modifier = modifier,
        transformation = transformation
    )
}
