package com.qx.orbit.bili.presentation

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.Reply
import com.qx.orbit.bili.data.model.VideoCard
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.about.AboutScreen
import com.qx.orbit.bili.presentation.player.PlayerScreen
import com.qx.orbit.bili.presentation.settings.SettingLoginStatusScreen
import com.qx.orbit.bili.presentation.settings.SettingPreferenceScreen
import com.qx.orbit.bili.presentation.settings.SettingTerminalPlayerScreen
import com.qx.orbit.bili.presentation.settings.SettingUIScreen
import com.qx.orbit.bili.presentation.settings.SettingVideoRenderScreen
import com.qx.orbit.bili.presentation.settings.SettingsScreen
import com.qx.orbit.bili.presentation.theme.OrbitTheme
import com.qx.orbit.bili.presentation.ui.components.LevelIcon
import com.qx.orbit.bili.presentation.ui.components.RecommendVideoCard
import com.qx.orbit.bili.presentation.ui.components.ShizukuActivationDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuNotInstalledDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuPermissionDialog
import com.qx.orbit.bili.presentation.ui.components.UserAvatar
import com.qx.orbit.bili.presentation.ui.components.UserNameText
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.viewmodel.DynamicFeedViewModel
import com.qx.orbit.bili.presentation.viewmodel.MainViewModel
import com.qx.orbit.bili.presentation.viewmodel.ReplyDetailViewModel
import com.qx.orbit.bili.presentation.viewmodel.SearchViewModel
import com.qx.orbit.bili.presentation.viewmodel.TabMode
import com.qx.orbit.bili.presentation.viewmodel.UserSpaceViewModel
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.ShizukuUtils
import com.qx.orbit.bili.util.VideoDownloadManager
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        CookieManager.init(this)
        SharedPreferencesUtil.init(this)
        VideoDownloadManager.init(this)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp(viewModel: MainViewModel = viewModel()) {
    OrbitTheme {
        val context = LocalContext.current
        var showShizukuDialog by remember { mutableStateOf(false) }
        var showShizukuNotInstalled by remember { mutableStateOf(false) }
        var showShizukuActivation by remember { mutableStateOf(false) }

        val storagePermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                // Permissions granted
            }
        }

        LaunchedEffect(Unit) {
            val hasDeclined = SharedPreferencesUtil.getBoolean("shizuku_declined", false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    storagePermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            } else {
                if (!ShizukuUtils.hasManageExternalStoragePermission(context) && !hasDeclined && !ShizukuUtils.isShizukuAuthorized()) {
                    showShizukuDialog = true
                }
            }
        }

        ShizukuPermissionDialog(
            show = showShizukuDialog,
            onDismissRequest = {
                    showShizukuDialog = false
                    SharedPreferencesUtil.putBoolean("shizuku_declined", true)
            },
            context = context,
            onConfirmAuth = {
                showShizukuDialog = false
                if (!ShizukuUtils.isShizukuAvailable()) {
                    if (ShizukuUtils.getShizukuVersionName(context) != null) {
                        showShizukuActivation = true
                    } else {
                        showShizukuNotInstalled = true
                    }
                } else {
                    try {
                        Shizuku.requestPermission(0)
                    } catch (e: Exception) {
                        ShizukuUtils.openShizukuManager(context)
                    }
                }
            }
        )

        ShizukuNotInstalledDialog(show = showShizukuNotInstalled, onDismissRequest = { showShizukuNotInstalled = false })


        ShizukuActivationDialog(
            show = showShizukuActivation,
            onDismissRequest = { showShizukuActivation = false },
            context = context,
            onShowNotInstalled = { showShizukuNotInstalled = true }
        )

        // Shizuku permission listener
        LaunchedEffect(Unit) {
            val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    ShizukuUtils.grantManageExternalStorage(context)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
        }

        val navController = rememberSwipeDismissableNavController()
        val currentBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry)
        val currentRoute = currentBackStackEntry?.destination?.route
        
        val isMiWatch5 = Build.MODEL == "M2505W1" || Build.MODEL == "M2501W1"
        val isSwipeEnabled = (currentRoute?.startsWith("player/") != true) && !isMiWatch5
        
        val canGoBack = remember(currentBackStackEntry) {
            navController.previousBackStackEntry != null
        }
        val backHandlerEnabled = canGoBack && isMiWatch5
        
        BackHandler(enabled = backHandlerEnabled) {
            navController.popBackStack()
        }
        
        AppScaffold(timeText = { WysTimeText() }) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "home",
                userSwipeEnabled = isSwipeEnabled,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
            composable("home") {
                HomeScreen(viewModel, navController)
            }
            composable(
                "dynamic_detail/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                DynamicDetailScreen(dynamicId = id, navController = navController)
            }
            composable(
                "opus_detail/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val idStr = backStackEntry.arguments?.getString("id") ?: ""
                val id = idStr.toLongOrNull() ?: 0L
                OpusDetailScreen(opusId = id, navController = navController)
            }
            composable(
                "article_detail/{id}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                ArticleDetailScreen(articleId = id, navController = navController)
            }
            composable(
                "live_room/{roomId}",
                arguments = listOf(
                    navArgument("roomId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
                LiveDetailScreen(roomId = roomId, navController = navController)
            }
            composable(
                "detail/{bvid}/{aid}",
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("aid") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: ""
                val aid = backStackEntry.arguments?.getLong("aid") ?: 0L
                VideoDetailScreen(navController = navController, bvid = bvid, aid = aid)
            }
            composable(
                "bangumi_detail/{media_id}",
                arguments = listOf(
                    navArgument("media_id") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getLong("media_id") ?: 0L
                BangumiDetailScreen(navController = navController, mediaId = mediaId)
            }
                composable("search") {
                    SearchInputScreen(navController = navController)
                }
                composable("search_result/{query}") { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    val searchViewModel: SearchViewModel = viewModel()
                    SearchResultScreen(viewModel = searchViewModel, query = query, navController = navController)
                }
                composable(
                    "reply_detail/{rpid}/{replyJson}",
                    arguments = listOf(
                        navArgument("rpid") { type = NavType.LongType },
                        navArgument("replyJson") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val json = backStackEntry.arguments?.getString("replyJson") ?: ""
                    val reply = Gson().fromJson(json, Reply::class.java)
                    val replyDetailViewModel: ReplyDetailViewModel = viewModel()
                    if (reply != null) {
                        ReplyDetailScreen(reply = reply, viewModel = replyDetailViewModel, navController = navController)
                    }
                }
                composable("login") {
                    LoginScreen(
                        navController = navController,
                        onLoginSuccess = {
                            viewModel.fetchNavInfo()
                            viewModel.loadMore(reset = true)
                        }
                    )
                }
            composable(
                "player/{playerDataJson}",
                arguments = listOf(
                    navArgument("playerDataJson") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val json = backStackEntry.arguments?.getString("playerDataJson") ?: ""
                val playerData = Gson().fromJson(json, PlayerData::class.java) ?: PlayerData(aid = 0L)
                PlayerScreen(initialData = playerData, onBack = { navController.popBackStack() })
            }
            composable(
                "user_space/{mid}",
                arguments = listOf(
                    navArgument("mid") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val mid = backStackEntry.arguments?.getLong("mid") ?: 0L
                val userSpaceViewModel: UserSpaceViewModel = viewModel()
                UserSpaceScreen(mid = mid, viewModel = userSpaceViewModel, navController = navController)
            }
            composable("download_manager") {
                DownloadManagerScreen(navController = navController)
            }
            composable("settings_main") {
                SettingsScreen(navController = navController)
            }
            composable("settings_terminal_player") {
                SettingTerminalPlayerScreen(navController = navController)
            }
            composable("settings_video_render") {
                SettingVideoRenderScreen(navController = navController)
            }
            composable("settings_ui") {
                SettingUIScreen(navController = navController)
            }
            composable("settings_preference") {
                SettingPreferenceScreen(navController = navController)
            }
            composable("settings_login_status") {
                SettingLoginStatusScreen(navController = navController)
            }
            composable("about") {
                AboutScreen(navController = navController)
            }
        }
    }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel, navController: NavHostController) {
    val currentTab by viewModel.currentTab.collectAsState()
    val videoList by viewModel.videoList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val navInfo by viewModel.navInfo.collectAsState()
    var showTabMenu by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showTabMenu) {
        if (!showTabMenu) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus exceptions
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentTab == TabMode.DYNAMIC) {
            val dynamicViewModel: DynamicFeedViewModel = viewModel()
            DynamicFeedScreen(
                viewModel = dynamicViewModel,
                focusRequester = focusRequester,
                navController = navController,
                onTabClick = { showTabMenu = true }
            )
        } else {
            RecommendScreen(
                currentTab = currentTab,
                videoList = videoList,
                isLoading = isLoading,
                errorMessage = errorMessage,
                focusRequester = focusRequester,
                navController = navController,
                onLoadMore = { viewModel.loadMore() },
                onTabClick = { showTabMenu = true },
                onRemoveVideo = { viewModel.removeAndDislikeVideo(it) }
            )
        }

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
                            if (navInfo == null || !navInfo!!.isLogin) {
                                viewModel.fetchNavInfo()
                            }
                            try { menuFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }

                    ScreenScaffold(
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
                                ListHeader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTabMenu = false }
                                        .transformedHeight(this, menuTransformationSpec),
                                    transformation = SurfaceTransformation(menuTransformationSpec)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(1.dp))
                                        Text(text = "菜单", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            item {
                                if (navInfo != null && navInfo!!.isLogin) {
                                    Button(
                                        onClick = {
                                            showTabMenu = false
                                            navController.navigate("user_space/${navInfo!!.mid}")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, menuTransformationSpec),
                                        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 14.dp),
                                        transformation = SurfaceTransformation(menuTransformationSpec)
                                    ) {
                                        UserAvatar(
                                            avatarUrl = navInfo!!.face ?: "",
                                            officialRole = 0,
                                            modifier = Modifier.size(36.dp),
                                            isVip = (navInfo!!.vip?.vipType ?: 0) > 0
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(verticalArrangement = Arrangement.Center) {
                                            UserNameText(
                                                name = navInfo!!.uname ?: "",
                                                isVip = (navInfo!!.vip?.vipType ?: 0) > 0,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val level = navInfo!!.level_info?.current_level ?: 0
                                            LevelIcon(
                                                level = level,
                                                isSenior = navInfo!!.is_senior_member == 1,
                                                modifier = Modifier.height(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            showTabMenu = false
                                            navController.navigate("login")
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, menuTransformationSpec),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        transformation = SurfaceTransformation(menuTransformationSpec)
                                    ) {
                                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "登录")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("登录")
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        showTabMenu = false
                                        navController.navigate("search")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, menuTransformationSpec),
                                    transformation = SurfaceTransformation(menuTransformationSpec)
                                ) {
                                    Icon(imageVector = Icons.Default.Search, modifier = Modifier.size(20.dp), contentDescription = "搜索")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("搜索")
                                }
                            }
                            items(TabMode.entries.size) { index ->
                                val tab = TabMode.entries[index]
                                Button(
                                    onClick = {
                                        viewModel.switchTab(tab)
                                        showTabMenu = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, menuTransformationSpec),
                                    transformation = SurfaceTransformation(menuTransformationSpec)
                                ) {
                                    when (tab) {
                                        TabMode.RECOMMEND -> {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = tab.title
                                            )
                                        }
                                        TabMode.POPULAR -> {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = tab.title
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Article,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = tab.title
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tab.title)
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        showTabMenu = false
                                        navController.navigate("download_manager")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, menuTransformationSpec),
                                    transformation = SurfaceTransformation(menuTransformationSpec)
                                ) {
                                    Icon(Icons.Default.Download, modifier = Modifier.size(20.dp), contentDescription = "缓存管理")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("缓存")
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        showTabMenu = false
                                        navController.navigate("settings_main")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, menuTransformationSpec),
                                    transformation = SurfaceTransformation(menuTransformationSpec)
                                ) {
                                    Icon(imageVector = Icons.Default.Settings, modifier = Modifier.size(20.dp), contentDescription = "设置")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("设置")
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun RecommendScreen(
    currentTab: TabMode,
    videoList: List<VideoCard>,
    isLoading: Boolean,
    errorMessage: String?,
    focusRequester: FocusRequester,
    navController: NavHostController,
    onLoadMore: () -> Unit,
    onTabClick: () -> Unit,
    onRemoveVideo: (VideoCard) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val titleHeight = 36.dp // Used to pad the list top so first item is visible
    
    // Dynamically measure actual title area height for complete hiding
    var actualTitleHeightPx by remember { mutableFloatStateOf(0f) }
    var titleOffset by remember { mutableFloatStateOf(0f) }

    var videoToDelete by remember { mutableStateOf<VideoCard?>(null) }

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
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + titleHeight,
                    bottom = contentPadding.calculateBottomPadding(),
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                ),
                state = listState
            , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
                items(videoList.size, key = { index -> videoList[index].bvid }) { index ->
                    if (index == videoList.size - 1 && !isLoading) {
                        LaunchedEffect(index) {
                            onLoadMore()
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
                            .animateItem()
                            .transformedHeight(this, transformationSpec),
                        primaryAction = {
                            PrimaryActionButton(
                                onClick = { 
                                    if (SharedPreferencesUtil.getBoolean("confirm_dislike", true)) {
                                        videoToDelete = videoList[index]
                                    } else {
                                        onRemoveVideo(videoList[index])
                                    }
                                },
                                icon = { Icon(Icons.Default.Delete, "Dislike") },
                                text = { Text("不感兴趣") },
                                modifier = Modifier.fillMaxHeight()
                            )
                        },
                        onSwipePrimaryAction = {
                            if (SharedPreferencesUtil.getBoolean("confirm_dislike", true)) {
                                videoToDelete = videoList[index]
                            } else {
                                onRemoveVideo(videoList[index])
                            }
                        }
                    ) {
                        RecommendVideoCard(
                            item = videoList[index],
                            onClick = {
                                navController.navigate("detail/${videoList[index].bvid}/${videoList[index].aid}")
                            },
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        )
                    }
                }
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (errorMessage != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { onLoadMore() },
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
                Text(text = currentTab.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }

    WysAlertDialog(
        show = videoToDelete != null,
        title = "确认操作",
        content = {
            Text(
                text = stringResource(R.string.clear_video_warning),
                textAlign = TextAlign.Center
            )
        },
        onDismissRequest = { videoToDelete = null },
        onConfirm = {
            videoToDelete?.let { onRemoveVideo(it) }
            videoToDelete = null
        }
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    // Empty preview since ViewModel is injected
}
