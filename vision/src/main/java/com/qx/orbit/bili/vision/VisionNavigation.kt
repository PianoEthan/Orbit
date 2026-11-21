package com.qx.orbit.bili.vision

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qx.orbit.bili.core.api.CoreVideo

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VisionNavigation() {
    val controller = rememberNavController()
    val historyModel: VisionPlaybackHistoryViewModel = viewModel()
    val cookie = VisionUserSession.cookie
    LaunchedEffect(cookie) { historyModel.setAccount(cookie) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val openImages: (List<String>, Int) -> Unit = { urls, index ->
        val sources = visionImageSources(urls)
        if (sources.isNotEmpty()) {
            controller.currentBackStackEntry?.savedStateHandle?.set("imageSources", ArrayList(sources))
            controller.navigate("images/${visionImagePage(index, 0, sources.size)}") { launchSingleTop = true }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = controller,
            startDestination = "home",
            enterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { it / 8 } },
            exitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { -it / 12 } },
            popEnterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { -it / 12 } },
            popExitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { it / 8 } },
        ) {
            composable("home") {
                VisionHomeScreen(
                    viewModel = viewModel(),
                    onVideoClick = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } },
                    onOpenSearch = { controller.navigate("search") { launchSingleTop = true } },
                    onOpenSettings = { controller.navigate("settings") { launchSingleTop = true } },
                    onOpenUserCenter = { controller.navigate("user") { launchSingleTop = true } },
                    onOpenSeasons = { controller.navigate("seasons") { launchSingleTop = true } },
                )
            }
            composable("search") {
                VisionSearchScreen(
                    onBack = { controller.popBackStack() },
                    onVideoClick = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } },
                    onRoomClick = { controller.navigate("live/${it.roomId}") { launchSingleTop = true } },
                    onUserClick = { controller.navigate("creator/${it.mid}") { launchSingleTop = true } },
                    onSeasonClick = { controller.navigate("season/${it.seasonId}") { launchSingleTop = true } },
                )
            }
            composable("user") {
                VisionUserCenterScreen(
                    onBack = { controller.popBackStack() },
                    onNavigateToLogin = { controller.navigate("login") { launchSingleTop = true } },
                    onVideoClick = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } },
                    onOpenDynamics = { controller.navigate("dynamics") { launchSingleTop = true } },
                )
            }
            composable("dynamics") {
                VisionDynamicScreen(onBack = { controller.popBackStack() },
                    onLogin = { controller.navigate("login") { launchSingleTop = true } },
                    onVideo = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } }, onImages = openImages)
            }
            composable("images/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
                val sources = remember(entry) {
                    entry.savedStateHandle.get<ArrayList<String>>("imageSources") ?: ArrayList(controller.previousBackStackEntry?.savedStateHandle?.get<ArrayList<String>>("imageSources").orEmpty()).also { entry.savedStateHandle["imageSources"] = it }
                }
                VisionImageScreen(sources, requireNotNull(entry.arguments).getInt("index"), onBack = { controller.popBackStack() })
            }
            composable("creator/{mid}", arguments = listOf(navArgument("mid") { type = NavType.LongType })) { entry ->
                VisionCreatorScreen(requireNotNull(entry.arguments).getLong("mid"),
                    onBack = { controller.popBackStack() }, onLogin = { controller.navigate("login") { launchSingleTop = true } },
                    onVideo = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } })
            }
            composable("seasons") {
                VisionSeasonCatalogScreen(onBack = { controller.popBackStack() },
                    onSeason = { controller.navigate("season/$it") { launchSingleTop = true } },
                    onLogin = { controller.navigate("login") { launchSingleTop = true } })
            }
            composable("season/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                VisionSeasonDetailScreen(requireNotNull(entry.arguments).getLong("id"), onBack = { controller.popBackStack() },
                    onPlay = { controller.navigate(videoRoute("player", it)) { launchSingleTop = true } },
                    onSeason = { controller.navigate("season/$it") { launchSingleTop = true } },
                    onLogin = { controller.navigate("login") { launchSingleTop = true } })
            }
            composable("login") {
                VisionLoginScreen(
                    onBack = { controller.popBackStack() },
                    onLoginSuccess = { controller.popBackStack() },
                )
            }
            composable("replies/{aid}/{root}", arguments = listOf(
                navArgument("aid") { type = NavType.LongType }, navArgument("root") { type = NavType.LongType },
            )) { entry ->
                val aid = requireNotNull(entry.arguments).getLong("aid")
                val root = requireNotNull(entry.arguments).getLong("root")
                val revision by entry.savedStateHandle.getStateFlow("replyRevision", 0).collectAsState()
                VisionReplyThreadScreen(aid, root, onBack = { controller.popBackStack() },
                    onComposeReply = { parent -> controller.navigate("composeReply/$aid/$root/$parent") { launchSingleTop = true } }, replyRevision = revision)
            }
            composable("composeReply/{aid}/{root}/{parent}", arguments = listOf(
                navArgument("aid") { type = NavType.LongType }, navArgument("root") { type = NavType.LongType }, navArgument("parent") { type = NavType.LongType },
            )) { entry ->
                val args = requireNotNull(entry.arguments)
                val owner = remember(entry) { controller.previousBackStackEntry ?: entry }
                val composer: VisionReplyComposerViewModel = viewModel(viewModelStoreOwner = owner, key = "reply-${args.getLong("aid")}-${args.getLong("root")}-${args.getLong("parent")}")
                val leave: () -> Unit = {
                    if (composer.state.value.sentId != null) {
                        owner.savedStateHandle["replyRevision"] = (owner.savedStateHandle.get<Int>("replyRevision") ?: 0) + 1
                        composer.clearSentResult()
                    }
                    controller.popBackStack()
                }
                VisionReplyComposerScreen(args.getLong("aid"), args.getLong("root"), args.getLong("parent"),
                    onBack = leave, onLogin = { controller.navigate("login") { launchSingleTop = true } }, onSent = leave, viewModel = composer)
            }
            composable("settings") {
                VisionSettingsScreen(
                    onBack = { controller.popBackStack() },
                    onOpenSection = { controller.navigate("settings/${it.name}") { launchSingleTop = true } },
                )
            }
            composable("settings/{section}", arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                val section = VisionSettingsSection.entries.firstOrNull { it.name == entry.arguments?.getString("section") }
                if (section != null) VisionSettingsSectionScreen(section, onBack = { controller.popBackStack() })
            }
            listOf("detail", "player").forEach { screen ->
                composable(
                    "$screen/{bvid}?cid={cid}&title={title}&cover={cover}&author={author}&aid={aid}&position={position}&episode={episode}&season={season}",
                    arguments = listOf(
                        navArgument("bvid") { type = NavType.StringType },
                        navArgument("cid") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("cover") { type = NavType.StringType; defaultValue = "" },
                        navArgument("author") { type = NavType.StringType; defaultValue = "" },
                        navArgument("aid") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("position") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("episode") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("season") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    val args = requireNotNull(entry.arguments)
                    val video = CoreVideo(
                        id = args.getLong("aid"), bvid = requireNotNull(args.getString("bvid")), cid = args.getLong("cid"),
                        title = args.getString("title").orEmpty(), cover = args.getString("cover").orEmpty(),
                        author = args.getString("author").orEmpty(), startPositionMs = args.getLong("position").coerceAtLeast(0),
                        episodeId = args.getLong("episode"), seasonId = args.getLong("season"),
                    )
                    val revision by entry.savedStateHandle.getStateFlow("replyRevision", 0).collectAsState()
                    if (screen == "detail") {
                        VisionDetailScreen(video, onBack = { controller.popBackStack() }, onPlay = {
                            controller.navigate(videoRoute("player", it)) { launchSingleTop = true }
                        }, viewModel = viewModel(), onLogin = { controller.navigate("login") { launchSingleTop = true } },
                            onReplyThread = { aid, root -> controller.navigate("replies/$aid/$root") { launchSingleTop = true } },
                            onComposeReply = { aid, root, parent -> controller.navigate("composeReply/$aid/$root/$parent") { launchSingleTop = true } }, replyRevision = revision, onCover = { openImages(listOf(it), 0) },
                            onCreator = { controller.navigate("creator/$it") { launchSingleTop = true } },
                            onVideo = { controller.navigate(videoRoute("detail", it)) { launchSingleTop = true } })
                    } else VisionPlayerScreen(video, onBack = { controller.popBackStack() }, historyModel = historyModel)
                }
            }
        }
    }
}

private fun videoRoute(screen: String, video: CoreVideo): String =
    "$screen/${Uri.encode(video.bvid)}?cid=${video.cid}&title=${Uri.encode(video.title)}&cover=${Uri.encode(video.cover)}&author=${Uri.encode(video.author)}&aid=${video.id}&position=${video.startPositionMs}&episode=${video.episodeId}&season=${video.seasonId}"
