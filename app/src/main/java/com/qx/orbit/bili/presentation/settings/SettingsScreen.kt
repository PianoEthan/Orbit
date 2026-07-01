package com.qx.orbit.bili.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TitleCard
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.compose.ui.graphics.graphicsLayer
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.util.SharedPreferencesUtil
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.wear.compose.material3.SurfaceTransformation
import com.qx.orbit.bili.presentation.MainActivity
import com.qx.orbit.bili.util.ShizukuUtils
import com.qx.orbit.bili.presentation.ui.components.ShizukuPermissionDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuNotInstalledDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuActivationDialog
import rikka.shizuku.Shizuku
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SettingsScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current
    val isLoggedIn = remember { CookieManager.getCookie().isNotEmpty() }

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = "设置", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (isLoggedIn) {
                item {
                    Button(
                        onClick = { navController.navigate("settings_login_status") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(text = "登录状态管理", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_terminal_player") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "内置播放器设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_ui") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "界面设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_preference") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "偏好设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("about") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "关于", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingPreferenceScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    var showShizukuDialog by remember { mutableStateOf(false) }
    var showShizukuNotInstalled by remember { mutableStateOf(false) }
    var showShizukuActivation by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(ShizukuUtils.hasManageExternalStoragePermission()) }

    // Shizuku permission listener
    LaunchedEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (ShizukuUtils.grantManageExternalStorage(context)) {
                    hasPermission = true
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
    }

    if (showShizukuDialog) {
        ShizukuPermissionDialog(
            show = true,
            onDismissRequest = { showShizukuDialog = false },
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
    }

    if (showShizukuNotInstalled) {
        ShizukuNotInstalledDialog(show = true, onDismissRequest = { showShizukuNotInstalled = false })
    }

    if (showShizukuActivation) {
        ShizukuActivationDialog(
            show = true,
            onDismissRequest = { showShizukuActivation = false },
            context = context,
            onShowNotInstalled = { showShizukuNotInstalled = true }
        )
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "偏好设置", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                var checked by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("use_hd_qr_code", true)) }
                SwitchButton(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        checked = isChecked
                        SharedPreferencesUtil.putBoolean("use_hd_qr_code", isChecked)
                    },
                    label = {
                        Text(text = "使用HD版登录接口", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            text = if (checked) "HD版风控更宽松" else "将使用传统网页端API",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                )
            }
            item {
                var checked by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("confirm_dislike", true)) }
                SwitchButton(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        checked = isChecked
                        SharedPreferencesUtil.putBoolean("confirm_dislike", isChecked)
                    },
                    label = {
                        Text(text = "拉黑视频需要二次确认", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            text = if (checked) "主页拉黑将弹窗确认" else "直接拉黑",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                )
            }

            item {
                Button(
                    onClick = {
                        if (!hasPermission) {
                            showShizukuDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "所有文件访问权限", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "缓存弹幕和字幕的必要权限",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (hasPermission) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = "Granted", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingTerminalPlayerScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "内置播放器设置", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Using SharedPreferencesUtil to track states with correct default values based on BiliClient
            val settings = listOf(
                Triple("player_longclick", "长按倍速", true),
                Triple("player_loop", "洗脑循环", false),
                Triple("player_background", "熄屏继续播放", false),
                Triple("player_autolandscape", "默认横屏", false),
                Triple("player_scale", "视频可缩放", true),
                Triple("player_doublemove", "缩放时可移动", true),
                Triple("player_danmaku_default_show", "弹幕默认开启", true),
                Triple("player_danmaku_allowoverlap", "弹幕允许重叠", true),
                Triple("player_danmaku_mergeduplicate", "合并重复弹幕", false),
                Triple("player_danmaku_showsender", "显示直播弹幕发送者", true),
                Triple("player_ui_showDanmakuBtn", "显示弹幕按钮", true)
            )
            item {
                val isLoggedIn = CookieManager.getCookie().contains("SESSDATA")
                val qualityOptions = listOf(
                    16 to "360p",
                    32 to "480p",
                    64 to "720p",
                    80 to "1080p"
                )
                var currentQn by remember { mutableIntStateOf(SharedPreferencesUtil.getInt("play_qn", 16)) }
                val currentIndex = qualityOptions.indexOfFirst { it.first == currentQn }.coerceAtLeast(0)

                Button(
                    onClick = {
                        val nextIndex = (currentIndex + 1) % qualityOptions.size
                        val nextQn = qualityOptions[nextIndex].first
                        if (nextQn >= 64 && !isLoggedIn) {
                            return@Button
                        }
                        currentQn = nextQn
                        SharedPreferencesUtil.putInt("play_qn", nextQn)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    val label = qualityOptions[currentIndex].second
                    Text(text = "视频清晰度: $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Button(
                    onClick = { navController.navigate("settings_video_render") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "视频渲染方式设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            settings.forEach { (key, label, defaultValue) ->
                item {
                    var checked by remember { mutableStateOf(SharedPreferencesUtil.getBoolean(key, defaultValue)) }
                    SwitchButton(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            checked = isChecked
                            SharedPreferencesUtil.putBoolean(key, isChecked)
                        },
                        label = {
                            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(transformationSpec) {
                                    applyContainerTransformation(scrollProgress)
                                }
                            }
                    )
                }
            }

            item {
                var maxLines by remember { mutableIntStateOf(SharedPreferencesUtil.getInt("player_danmaku_maxline", 0)) }
                Button(
                    onClick = {
                        val nextLines = when (maxLines) {
                            0 -> 5
                            5 -> 10
                            10 -> 20
                            else -> 0
                        }
                        maxLines = nextLines
                        SharedPreferencesUtil.putInt("player_danmaku_maxline", nextLines)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    val label = if (maxLines == 0) "不限" else "${maxLines}行"
                    Text(text = "弹幕密度: $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingUIScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "界面设置", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                var checked by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("ui_immersive_time", true)) }
                SwitchButton(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        checked = isChecked
                        SharedPreferencesUtil.putBoolean("ui_immersive_time", isChecked)
                    },
                    label = {
                        Text(text = "沉浸式时间显示", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun SettingLoginStatusScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "登录状态管理", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                Button(
                    onClick = {
                        val cookieString = CookieManager.getCookie()
                        if (cookieString.isEmpty()) {
                            RoundToast.show(context, "当前未登录")
                            return@Button
                        }
                        val items = cookieString.split("; ").filter { it.contains("=") }.map {
                            val idx = it.indexOf("=")
                            CookieExportItem(name = it.substring(0, idx), value = it.substring(idx + 1))
                        }
                        val json = Gson().toJson(CookieExportData(cookies = items))
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Bilibili Cookie", json))
                        RoundToast.show(context, "Cookie 已复制到剪贴板")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "导出 Cookie", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = {
                        CookieManager.clearCookie()
                        RoundToast.show(context, "已退出登录")
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "退出登录", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class CookieExportData(
    val cookies: List<CookieExportItem>? = null,
)

private data class CookieExportItem(
    val name: String = "",
    val value: String = "",
)

@Composable
fun SettingVideoRenderScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var useTextureView by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_texture_view", false)) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                ) {
                    Text(text = "视频渲染", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                TitleCard(
                    onClick = {
                        useTextureView = false
                        SharedPreferencesUtil.putBoolean("player_texture_view", false)
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!useTextureView) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SurfaceView")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = if (!useTextureView) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "性能更好，但部分设备可能存在兼容性问题",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                TitleCard(
                    onClick = {
                        useTextureView = true
                        SharedPreferencesUtil.putBoolean("player_texture_view", true)
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (useTextureView) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TextureView")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = if (useTextureView) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "兼容性较好，但会带来额外性能开销",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Text(
                    text = "如果视频播放异常/闪退/黑屏/绿屏/无法缩放，请尝试切换该选项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(transformationSpec) {
                                applyContainerTransformation(scrollProgress)
                            }
                        }
                )
            }
        }
    }
}
