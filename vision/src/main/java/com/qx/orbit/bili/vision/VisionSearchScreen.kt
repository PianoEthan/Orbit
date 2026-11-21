package com.qx.orbit.bili.vision

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qx.orbit.bili.core.api.CoreHotword
import com.qx.orbit.bili.core.api.CoreLiveRoom
import com.qx.orbit.bili.core.api.CoreSearchSeason
import com.qx.orbit.bili.core.api.CoreSearchUser
import com.qx.orbit.bili.core.api.CoreVideo

@Composable
internal fun VisionSearchScreen(
    onBack: () -> Unit,
    onVideoClick: (CoreVideo) -> Unit,
    onRoomClick: (CoreLiveRoom) -> Unit = {},
    onUserClick: (CoreSearchUser) -> Unit = {},
    onSeasonClick: (CoreSearchSeason) -> Unit = {},
    viewModel: VisionSearchViewModel = viewModel(),
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var nineKey by rememberSaveable { mutableStateOf(isTv || VisionPreferences.preferNineKey) }

    val text by viewModel.text.collectAsState()
    val query by viewModel.query.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val hotwords by viewModel.hotwords.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val results by viewModel.results.collectAsState()
    val roomResults by viewModel.roomResults.collectAsState()
    val userResults by viewModel.userResults.collectAsState()
    val seasonResults by viewModel.seasonResults.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val history = VisionSearchHistory.history

    val keyboard = LocalSoftwareKeyboardController.current

    val submitSearch: (String) -> Unit = { keyword ->
        val trimmed = keyword.trim()
        if (trimmed.isNotBlank()) {
            keyboard?.hide()
            VisionSearchHistory.add(context, trimmed)
            viewModel.submit(trimmed)
        }
    }

    BackHandler(onBack = {
        if (query != null) {
            viewModel.clearSearch()
        } else {
            onBack()
        }
    })

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val screenWidthDp = maxWidth.value
            val wide = maxWidth >= 600.dp || maxHeight < 480.dp

            if (wide) {
                // Landscape / TV Layout: Split into Left (Search + Keyboard) and Right (Hotwords / Suggestions / Results)
                Row(Modifier.fillMaxSize()) {
                    // Left Column
                    Column(
                        Modifier
                            .widthIn(min = 320.dp, max = 380.dp)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchInputHeader(
                            text = text,
                            onTextChange = viewModel::onTextChanged,
                            onSearch = { submitSearch(text) },
                            onBack = onBack,
                            nineKey = nineKey,
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FilterChip(
                                selected = nineKey,
                                onClick = {
                                    nineKey = !nineKey
                                    keyboard?.hide()
                                },
                                label = { Text(if (nineKey) "九键输入 (遥控器)" else "系统输入法") },
                            )
                            if (text.isNotEmpty()) {
                                TextButton(onClick = { viewModel.onTextChanged("") }) {
                                    Text("清空输入")
                                }
                            }
                        }

                        if (nineKey) {
                            VisionNineKeyKeyboard(
                                onCharacter = { viewModel.onTextChanged(text + it) },
                                onDelete = { viewModel.onTextChanged(deleteSearchCharacter(text)) },
                                onSearch = { submitSearch(text) },
                                canSearch = text.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "当前已启用系统输入法。\n您可直接在上方搜索框输入，或点击上方标签切回九键。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Vertical Divider
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // Right Column (Results / Suggestions / Hotwords)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        SearchRightPane(
                            query = query,
                            text = text,
                            tab = tab,
                            onSelectTab = viewModel::switchTab,
                            hotwords = hotwords,
                            suggestions = suggestions,
                            results = results,
                            roomResults = roomResults,
                            userResults = userResults,
                            seasonResults = seasonResults,
                            loading = loading,
                            error = error,
                            hasMore = hasMore,
                            history = history,
                            onSelectKeyword = submitSearch,
                            onClearSearch = viewModel::clearSearch,
                            onRefreshHotwords = viewModel::loadHotwords,
                            onClearHistory = { VisionSearchHistory.clear(context) },
                            onLoadMore = viewModel::loadMore,
                            onRetry = viewModel::retry,
                            onVideoClick = onVideoClick,
                            onRoomClick = onRoomClick,
                            onUserClick = onUserClick,
                            onSeasonClick = onSeasonClick,
                            columns = visionGridColumns((screenWidthDp - 380f).coerceAtLeast(300f)),
                        )
                    }
                }
            } else {
                // Portrait / Phone Layout: Compact Stack
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchInputHeader(
                            text = text,
                            onTextChange = viewModel::onTextChanged,
                            onSearch = { submitSearch(text) },
                            onBack = onBack,
                            nineKey = nineKey,
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FilterChip(
                                selected = nineKey,
                                onClick = {
                                    nineKey = !nineKey
                                    keyboard?.hide()
                                },
                                label = { Text(if (nineKey) "九键输入" else "系统输入法") },
                            )
                            if (text.isNotEmpty()) {
                                TextButton(onClick = { viewModel.onTextChanged("") }) {
                                    Text("清空")
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        SearchRightPane(
                            query = query,
                            text = text,
                            tab = tab,
                            onSelectTab = viewModel::switchTab,
                            hotwords = hotwords,
                            suggestions = suggestions,
                            results = results,
                            roomResults = roomResults,
                            userResults = userResults,
                            seasonResults = seasonResults,
                            loading = loading,
                            error = error,
                            hasMore = hasMore,
                            history = history,
                            onSelectKeyword = submitSearch,
                            onClearSearch = viewModel::clearSearch,
                            onRefreshHotwords = viewModel::loadHotwords,
                            onClearHistory = { VisionSearchHistory.clear(context) },
                            onLoadMore = viewModel::loadMore,
                            onRetry = viewModel::retry,
                            onVideoClick = onVideoClick,
                            onRoomClick = onRoomClick,
                            onUserClick = onUserClick,
                            onSeasonClick = onSeasonClick,
                            columns = visionGridColumns(screenWidthDp),
                        )
                    }

                    if (nineKey) {
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            VisionNineKeyKeyboard(
                                onCharacter = { viewModel.onTextChanged(text + it) },
                                onDelete = { viewModel.onTextChanged(deleteSearchCharacter(text)) },
                                onSearch = { submitSearch(text) },
                                canSearch = text.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputHeader(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    nineKey: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("搜索视频、UP主与番剧") },
            singleLine = true,
            readOnly = nineKey,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = MaterialTheme.shapes.extraLarge,
            trailingIcon = {
                if (text.isNotEmpty() && !nineKey) {
                    IconButton(onClick = { onTextChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除输入")
                    }
                } else {
                    IconButton(onClick = onSearch, enabled = text.isNotBlank()) {
                        Icon(Icons.Default.Search, contentDescription = "提交搜索")
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SearchRightPane(
    query: String?,
    text: String,
    tab: VisionSearchTab = VisionSearchTab.VIDEO,
    onSelectTab: (VisionSearchTab) -> Unit = {},
    hotwords: List<CoreHotword> = emptyList(),
    suggestions: List<String> = emptyList(),
    results: List<CoreVideo> = emptyList(),
    roomResults: List<CoreLiveRoom> = emptyList(),
    userResults: List<CoreSearchUser> = emptyList(),
    seasonResults: List<CoreSearchSeason> = emptyList(),
    loading: Boolean = false,
    error: String? = null,
    hasMore: Boolean = false,
    history: List<String> = emptyList(),
    onSelectKeyword: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onRefreshHotwords: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onVideoClick: (CoreVideo) -> Unit = {},
    onRoomClick: (CoreLiveRoom) -> Unit = {},
    onUserClick: (CoreSearchUser) -> Unit = {},
    onSeasonClick: (CoreSearchSeason) -> Unit = {},
    columns: Int = 2,
) {
    val activeItems: List<Any> = when (tab) {
        VisionSearchTab.VIDEO -> results
        VisionSearchTab.LIVE -> roomResults
        VisionSearchTab.USER -> userResults
        VisionSearchTab.SEASON -> seasonResults
    }
    when {
        // State A: Search results loaded or in progress
        query != null -> {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "“$query” 的搜索结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearSearch) {
                        Text("返回热搜")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisionSearchTab.entries.forEach { option ->
                        FilterChip(
                            selected = option == tab,
                            onClick = { onSelectTab(option) },
                            label = { Text(option.title) },
                        )
                    }
                }

                if (activeItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            error != null -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(error, color = MaterialTheme.colorScheme.error)
                                    Button(onClick = onRetry) { Text("重试搜索") }
                                }
                            }
                            else -> Text("未找到相关${tab.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (tab == VisionSearchTab.VIDEO) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    ) {
                        items(results, key = { it.bvid }) { video ->
                            VisionExpressiveVideoCard(video = video, onClick = { onVideoClick(video) })
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchLoadMoreFooter(loading, error, hasMore, onRetry, onLoadMore)
                        }
                    }
                } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    val roomRow: @Composable (CoreLiveRoom) -> Unit = { room ->
                        SearchResultRow(
                            imageUrl = room.cover.ifBlank { room.face },
                            title = room.title.ifBlank { room.uname },
                            subtitle = buildString {
                                append(room.uname)
                                if (room.areaName.isNotBlank()) append(" · ").append(room.areaName)
                            },
                            trailing = if (room.liveStatus == 1) "直播中 · ${formatVisionOnline(room.online)}" else "已下播",
                            live = room.liveStatus == 1,
                            onClick = { onRoomClick(room) },
                        )
                    }
                    val userRow: @Composable (CoreSearchUser) -> Unit = { user ->
                        SearchResultRow(
                            imageUrl = user.face,
                            title = user.name,
                            subtitle = user.sign,
                            trailing = "${formatVisionCount(user.fans)} 粉丝",
                            roundedImage = true,
                            onClick = { onUserClick(user) },
                        )
                    }
                    val seasonRow: @Composable (CoreSearchSeason) -> Unit = { season ->
                        SearchResultRow(
                            imageUrl = season.cover,
                            title = season.title,
                            subtitle = listOf(season.areas, season.subtitle)
                                .filter(String::isNotBlank).joinToString(" · "),
                            onClick = { onSeasonClick(season) },
                        )
                    }
                    val rowColumns = visionListColumns(maxWidth.value)
                    if (rowColumns == 1) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            when (tab) {
                                VisionSearchTab.LIVE -> items(roomResults, key = { it.roomId }) { room -> roomRow(room) }
                                VisionSearchTab.USER -> items(userResults, key = { it.mid }) { user -> userRow(user) }
                                VisionSearchTab.SEASON -> items(seasonResults, key = { it.seasonId }) { season -> seasonRow(season) }
                                VisionSearchTab.VIDEO -> Unit
                            }
                            item { SearchLoadMoreFooter(loading, error, hasMore, onRetry, onLoadMore) }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(rowColumns),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            when (tab) {
                                VisionSearchTab.LIVE -> items(roomResults, key = { it.roomId }) { room -> roomRow(room) }
                                VisionSearchTab.USER -> items(userResults, key = { it.mid }) { user -> userRow(user) }
                                VisionSearchTab.SEASON -> items(seasonResults, key = { it.seasonId }) { season -> seasonRow(season) }
                                VisionSearchTab.VIDEO -> Unit
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchLoadMoreFooter(loading, error, hasMore, onRetry, onLoadMore)
                            }
                        }
                    }
                }
            }
        }

        // State B: Typing with autocomplete suggestions
        text.isNotBlank() -> {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = "搜索建议",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (suggestions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("按确认键或点击直接搜索：“$text”", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(suggestions) { item ->
                            SuggestionRow(suggestion = item, onClick = { onSelectKeyword(item) })
                        }
                    }
                }
            }
        }

        // State C: Initial state - History and Hotwords
        else -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // History Section
                if (history.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    "搜索历史",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(onClick = onClearHistory) {
                                Text("清空")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            history.forEach { item ->
                                SuggestionChip(
                                    onClick = { onSelectKeyword(item) },
                                    label = { Text(item) },
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }
                    }
                }

                // Hotwords Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "热搜榜单",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onRefreshHotwords) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新热搜")
                        }
                    }

                    if (hotwords.isEmpty()) {
                        Text(
                            "正在加载热搜...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            hotwords.forEachIndexed { index, hotword ->
                                HotwordRow(
                                    rank = index + 1,
                                    hotword = hotword,
                                    onClick = { onSelectKeyword(hotword.keyword) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLoadMoreFooter(
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = onRetry) { Text("重试加载更多") }
            }
            hasMore -> FilledTonalButton(onClick = onLoadMore) { Text("加载更多") }
            else -> Text("已加载全部内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SearchResultRow(
    imageUrl: String,
    title: String,
    subtitle: String,
    trailing: String = "",
    live: Boolean = false,
    roundedImage: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = remember(imageUrl, roundedImage, context) {
                    if (imageUrl.isBlank()) null else ImageRequest.Builder(context)
                        .data(if (roundedImage) imageUrl else visionThumbnailUrl(imageUrl, 240))
                        .size(if (roundedImage) 160 else 240)
                        .setHeader("Referer", "https://www.bilibili.com/").crossfade(false).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 72.dp, height = 48.dp)
                    .clip(if (roundedImage) CircleShape else MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing.isNotBlank()) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun formatVisionCount(count: Int): String = when {
    count >= 100_000_000 -> String.format(java.util.Locale.ROOT, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(java.util.Locale.ROOT, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

internal fun formatVisionOnline(online: Int): String = formatVisionCount(online)

@Composable
private fun SuggestionRow(suggestion: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyLarge,
                color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HotwordRow(rank: Int, hotword: CoreHotword, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isTop3 = rank <= 3

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            Text(
                text = hotword.showName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun VisionNineKeyKeyboard(
    onCharacter: (String) -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    canSearch: Boolean,
    modifier: Modifier = Modifier,
) {
    var numeric by rememberSaveable { mutableStateOf(false) }
    var activeGroup by rememberSaveable { mutableStateOf<Int?>(null) }
    var returnGroup by rememberSaveable { mutableStateOf<Int?>(null) }
    val requesters = remember { List(9) { FocusRequester() } }
    val inputMode = LocalInputModeManager.current

    LaunchedEffect(activeGroup, returnGroup) {
        if (activeGroup == null && returnGroup != null && inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            requesters[returnGroup!!].requestFocus()
            returnGroup = null
        }
    }

    // Outer Box: contains 3x3 keys AND in-place floating overlay (浮层，非 Dialog 弹窗)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 3x3 Grid
            Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { col ->
                                val index = row * 3 + col
                                FilledTonalButton(
                                    onClick = {
                                        if (numeric) {
                                            onCharacter((index + 1).toString())
                                        } else {
                                            returnGroup = index
                                            activeGroup = index
                                        }
                                    },
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 54.dp)
                                        .focusRequester(requesters[index]),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = if (numeric) (index + 1).toString() else visionLetterGroups[index],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                // IN-PLACE FLOATING OVERLAY (内联浮层，非 Dialog 弹窗)
                activeGroup?.let { groupIndex ->
                    NineKeyInlineOverlay(
                        group = visionLetterGroups[groupIndex],
                        onDismiss = { activeGroup = null },
                        onSelect = { char ->
                            onCharacter(char)
                            activeGroup = null
                        },
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(10f),
                    )
                }
            }

            // Bottom control row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { numeric = !numeric },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (numeric) "ABC" else "123")
                }
                OutlinedButton(
                    onClick = { onCharacter(if (numeric) "0" else " ") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (numeric) "0" else "空格")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(6.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "退格")
                }
            }

            // Search execute button
            Button(
                onClick = onSearch,
                enabled = canSearch,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("搜索")
            }
        }
    }
}

/**
 * 内联浮层：直接覆盖在九键键盘上方，截获遥控器方向键，选字即走，不创建新 Window，不弹全屏遮罩
 */
@Composable
private fun NineKeyInlineOverlay(
    group: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        overlayFocus.requestFocus()
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val direction = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> NineKeyDirection.UP
                    KeyEvent.KEYCODE_DPAD_RIGHT -> NineKeyDirection.RIGHT
                    KeyEvent.KEYCODE_DPAD_DOWN -> NineKeyDirection.DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> NineKeyDirection.LEFT
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        if (native.action == KeyEvent.ACTION_UP) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    else -> null
                }
                if (direction != null) {
                    if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                        nineKeyCharacter(group, direction)?.let(onSelect) ?: onDismiss()
                    }
                    true
                } else false
            }
            .focusRequester(overlayFocus)
            .focusable(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.98f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        tonalElevation = 6.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = "按方向键选字母: $group",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            // Up
            LetterChoiceButton(group, NineKeyDirection.UP, "↑", onSelect)

            // Left - Center Cancel - Right
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LetterChoiceButton(group, NineKeyDirection.LEFT, "←", onSelect)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("取消", style = MaterialTheme.typography.bodySmall)
                }
                LetterChoiceButton(group, NineKeyDirection.RIGHT, "→", onSelect)
            }

            // Down
            LetterChoiceButton(group, NineKeyDirection.DOWN, "↓", onSelect)
        }
    }
}

@Composable
private fun LetterChoiceButton(
    group: String,
    direction: NineKeyDirection,
    arrow: String,
    onSelect: (String) -> Unit,
) {
    val char = nineKeyCharacter(group, direction)
    FilledTonalButton(
        onClick = { char?.let(onSelect) },
        enabled = char != null,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            text = "$arrow ${char.orEmpty()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
