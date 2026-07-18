@file:Suppress("unused")

package com.qx.orbit.bili.presentation.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.google.gson.Gson
import com.qx.orbit.bili.R
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.MainActivity
import com.qx.orbit.bili.presentation.theme.LocalScreenRound
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.presentation.ui.components.ShizukuActivationDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuNotInstalledDialog
import com.qx.orbit.bili.presentation.ui.components.ShizukuPermissionDialog
import com.qx.orbit.bili.presentation.ui.components.WysAlertDialog
import com.qx.orbit.bili.presentation.ui.components.WysTimeText
import com.qx.orbit.bili.presentation.ui.components.adaptiveTransformedHeight
import com.qx.orbit.bili.presentation.util.rememberSafeRotaryScrollableBehavior
import com.qx.orbit.bili.util.AppConfig
import com.qx.orbit.bili.util.ScreenMode
import com.qx.orbit.bili.BuildConfig
import com.qx.orbit.bili.util.PlayerConfig
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.ShizukuUtils
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import com.qx.orbit.bili.data.api.CookieRefreshApi

@Composable
fun SettingsScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
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
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
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
                            .adaptiveTransformedHeight(this, transformationSpec),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null
                    ) {
                        Text(text = "登录状态管理", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_player_choose") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "选择播放器", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_apsis_player") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "Apsis Player 设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = { navController.navigate("settings_ui") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "关于软件", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val isRound = LocalScreenRound.current

    ScreenScaffold(
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            
            item {
                var checked by remember { mutableStateOf(PlayerConfig.isTryLookEnabled()) }
                SwitchButton(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        checked = isChecked
                        PlayerConfig.setTryLookEnabled(isChecked)
                    },
                    label = {
                        Text(text = "免登录观看1080P", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            text = if (checked) "未登录时可观看1080P视频" else "需要登录才能观看1080P",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }


            item {
                Button(
                    onClick = { navController.navigate("settings_cache_location") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                ) {
                    Text(text = "缓存位置管理", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingApsisPlayerScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

    ScreenScaffold(
        timeText = { WysTimeText() },
        scrollState = listState
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "Apsis Player 设置", color = MaterialTheme.colorScheme.primary)
                }
            }
            // 应用图标和名称
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                        .adaptiveTransformedHeight(this, transformationSpec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.apsis),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(64.dp)
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                        .adaptiveTransformedHeight(this, transformationSpec),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Apsis Player", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.W700)
                    Text(text = BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp).adaptiveTransformedHeight(this, transformationSpec)) }

            // Using SharedPreferencesUtil to track states with correct default values based on BiliClient
            val settings = listOf(
                Triple("player_longclick", "长按倍速", true),
                Triple("player_loop", "洗脑循环", false),
                Triple("player_background", "熄屏继续播放", false),
                Triple("player_background_audio_only", "后台播放音频", false),
                Triple("player_autolandscape", "默认横屏", false),
                Triple("player_softrotate", "软件旋屏", false),
                Triple("player_scale", "视频可缩放", true),
                Triple("player_doublemove", "缩放时可移动", true),
                Triple("player_one_finger_zoom", "单指缩放", false),
                Triple("player_request_dash", "请求 DASH 格式视频 (更高画质)", false),
                Triple("player_danmaku_default_show", "弹幕默认开启", true),
                Triple("player_subtitle_default_show", "CC字幕默认开启", false),
                Triple("player_danmaku_advanced_enable", "启用高级弹幕", true),
                Triple("player_danmaku_allowoverlap", "弹幕允许重叠", true),
                Triple("player_danmaku_mergeduplicate", "合并重复弹幕", false),
                Triple("player_danmaku_showsender", "显示直播弹幕发送者", true),
                Triple("player_ui_showDanmakuBtn", "显示弹幕按钮", true)
            )
            item {
                Button(
                    onClick = { navController.navigate("settings_player_customization") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "播放器界面自定义", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Rotate direction settings
            item {
                var showRotateStepsDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showRotateStepsDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "旋转方向设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val rotateSteps = listOf("竖屏 (0°)" to "0", "横屏 (90°)" to "90", "反向竖屏 (180°)" to "180", "反向横屏 (270°)" to "270")
                    var selectedSteps by remember {
                        val saved = SharedPreferencesUtil.getString("player_softrotate_steps", "0,90,180,270")
                        mutableStateOf(saved.split(",").map { it.trim() }.toSet())
                    }
                    Dialog(
                        visible = showRotateStepsDialog,
                        onDismissRequest = { showRotateStepsDialog = false }
                    ) {
                        val listState = rememberTransformingLazyColumnState()
                        TransformingLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            item {
                                ListHeader {
                                    Text(text = "旋转方向", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            itemsIndexed(rotateSteps) { _, (label, value) ->
                                val isSelected = selectedSteps.contains(value)
                                Button(
                                    onClick = {
                                        val newSteps = if (isSelected) {
                                            if (selectedSteps.size > 2) selectedSteps - value else selectedSteps
                                        } else {
                                            selectedSteps + value
                                        }
                                        selectedSteps = newSteps
                                        val sorted = newSteps.mapNotNull { it.toIntOrNull() }.sorted()
                                        SharedPreferencesUtil.putString("player_softrotate_steps", sorted.joinToString(","))
                                    },
                                    colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                                    icon = { if (isSelected) Icon(Icons.Default.Check, contentDescription = null) },
                                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                                    modifier = Modifier.fillMaxWidth().adaptiveTransformedHeight(this, transformationSpec)
                                ) {
                                    Text(text = label)
                                }
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
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
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                ) {
                    Text(text = "视频渲染方式设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            //if (BuildConfig.HAS_IJK) {
                item {
                    Button(
                        onClick = { navController.navigate("settings_player_engine") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec)
                    ) {
                        Text(text = "播放引擎设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            //}

            item {
                Button(
                    onClick = { navController.navigate("settings_danmaku_engine") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "弹幕引擎设置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }


            settings.forEach { (key, label, defaultValue) ->
                item {
                    val currentEngine = SharedPreferencesUtil.getString("player_engine", "ijk")
                    var checked by remember { mutableStateOf(SharedPreferencesUtil.getBoolean(key, defaultValue)) }
                    SwitchButton(
                        checked = checked,
                        enabled = (key != "player_request_dash" || currentEngine == "media3"),
                        onCheckedChange = { isChecked ->
                            checked = isChecked
                            SharedPreferencesUtil.putBoolean(key, isChecked)
                            if (key == "player_one_finger_zoom" && isChecked) {
                                navController.navigate("one_finger_zoom_tutorial")
                            }
                        },
                        label = {
                            Text(text = label, maxLines = 1, modifier = Modifier.basicMarquee())
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec)
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
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                ) {
                    val label = if (maxLines == 0) "不限" else "${maxLines}行"
                    Text(text = "弹幕密度: $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                var scaleTextSize by remember {
                    mutableFloatStateOf(SharedPreferencesUtil.getFloat("player_danmaku_textsize", 1.0f))
                }
                TitleCard(
                    onClick = {},
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("弹幕字号", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = String.format(LocalLocale.current.platformLocale, "%.1f", scaleTextSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = scaleTextSize,
                                onValueChange = { scaleTextSize = it },
                                onValueChangeFinished = {
                                    SharedPreferencesUtil.putFloat("player_danmaku_textsize", scaleTextSize)
                                },
                                valueRange = 0.5f..2.0f,
                                steps = 14,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingUIScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                )
            }
            item {
                val context = LocalContext.current
                Button(
                    onClick = {
                        val nextMode = when (AppConfig.screenMode) {
                            ScreenMode.AUTO -> ScreenMode.ROUND
                            ScreenMode.ROUND -> ScreenMode.SQUARE
                            ScreenMode.SQUARE -> ScreenMode.AUTO
                        }
                        AppConfig.saveScreenMode(context, nextMode)
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ){
                    Column {
                        Text("屏幕形状模式", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = when (AppConfig.screenMode) {
                                ScreenMode.AUTO -> "自动检测"
                                ScreenMode.ROUND -> "强制圆屏"
                                ScreenMode.SQUARE -> "强制方屏"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@SuppressLint("WearRecents")
@Composable
fun SettingLoginStatusScreen(navController: NavController) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
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
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "导出 Cookie", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Button(
                    onClick = {
                        if (isRefreshing) return@Button
                        isRefreshing = true
                        RoundToast.show(context, "正在刷新 Cookie...")
                        coroutineScope.launch {
                            val result = CookieRefreshApi.doCookieRefresh(auto = false)
                            RoundToast.show(context, result.message)
                            isRefreshing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(text = "手动刷新 Cookie", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "退出登录", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Text(
                    text = "提示：Cookie 为用户的登录凭证，可在 Orbit 中导入 Cookie 登录，便于将当前登录信息迁移到另一台设备；\n导入到另一台设备后，必须在这台设备上退出登录；\n请妥善保存你的 Cookie，不要与他人共享 Cookie，或将其暴露在浏览器或其他客户端代码中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        
        WysAlertDialog(
            show = showLogoutDialog,
            onDismissRequest = { showLogoutDialog = false },
            title = "退出登录",
            content = { Text("确定要退出当前登录账号吗？", textAlign = TextAlign.Center) },
            onConfirm = {
                showLogoutDialog = false
                CookieManager.clearCookie()
                RoundToast.show(context, "已退出登录")
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )
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
    val isRound = LocalScreenRound.current
    var useTextureView by remember { mutableStateOf(SharedPreferencesUtil.getBoolean("player_texture_view", true)) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
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
                        .adaptiveTransformedHeight(this, transformationSpec),
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
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
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
                        .adaptiveTransformedHeight(this, transformationSpec),
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
                    text = "如果视频播放异常/闪退/黑屏/绿屏/比例异常，请尝试切换该选项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .adaptiveTransformedHeight(this, transformationSpec)
                        .graphicsLayer { if (isRound) { with(transformationSpec) { applyContainerTransformation(scrollProgress) } } }
                )
            }
        }
    }
}

@Composable
fun SettingDanmakuEngineScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var currentDanmakuEngine by remember { mutableStateOf(SharedPreferencesUtil.getString("danmaku_engine", "dfm")) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "弹幕引擎", color = MaterialTheme.colorScheme.primary)
                }
            }

            val engineOptions = listOf("dfm" to "烈焰弹幕使", "dfmnext" to "DFM-Next(实验性)")
            val engineDescriptions = mapOf(
                "dfm" to "由B站官方出品，稳定的老牌弹幕引擎",
                "dfmnext" to "使用Kotlin完全重构烈焰弹幕使，并且加入了较为激进的优化策略"
            )

            engineOptions.forEach { (value, label) ->
                item {
                    TitleCard(
                        onClick = {
                            currentDanmakuEngine = value
                            SharedPreferencesUtil.putString("danmaku_engine", value)
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentDanmakuEngine == value) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "已选择",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = engineDescriptions[value] ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        colors = if (currentDanmakuEngine == value) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else CardDefaults.cardColors()
                    )
                }
            }

        }
    }
}

@Composable
fun SettingPlayerEngineScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var currentEngine by remember { mutableStateOf(SharedPreferencesUtil.getString("player_engine", "ijk")) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "播放引擎", color = MaterialTheme.colorScheme.primary)
                }
            }

            val engineOptions = listOf(
                "ijk" to "IJKPlayer",
                "media3" to "Media3 (ExoPlayer)"
            )
            val engineDescriptions = mapOf(
                "ijk" to if (BuildConfig.HAS_IJK) "基于 FFmpeg 的播放引擎，兼容性最好"
                         else "基于 FFmpeg 的播放引擎，兼容性最好\n[当前构建不支持IJKPlayer]",
                "media3" to "Google 官方的播放引擎，支持 DASH 自适应码率"
            )

            engineOptions.forEach { (value, label) ->
                val isDisabled = value == "ijk" && !BuildConfig.HAS_IJK
                item {
                    TitleCard(
                        onClick = {
                            if (!isDisabled) {
                                currentEngine = value
                                SharedPreferencesUtil.putString("player_engine", value)
                            }
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentEngine == value && !isDisabled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "已选择",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color.Unspecified)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = engineDescriptions[value] ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        colors = when {
                            currentEngine == value && !isDisabled -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            isDisabled -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            else -> CardDefaults.cardColors()
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SettingCacheLocationScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var cacheLocation by remember { mutableStateOf(SharedPreferencesUtil.getString("cache_location", "internal")) }
    val context = LocalContext.current
    
    var showShizukuDialog by remember { mutableStateOf(false) }
    var showShizukuNotInstalled by remember { mutableStateOf(false) }
    var showShizukuActivation by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(ShizukuUtils.hasManageExternalStoragePermission(context)) }

    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            hasPermission = true
        }
    }

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
            modifier = Modifier.fillMaxWidth(),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "视频缓存位置", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                TitleCard(
                    onClick = {
                        cacheLocation = "internal"
                        SharedPreferencesUtil.putString("cache_location", "internal")
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (cacheLocation == "internal") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("内部存储")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    colors = if (cacheLocation == "internal") {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "应用专有目录 (无需额外授权, 卸载应用时会同时删除)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                TitleCard(
                    onClick = {
                        cacheLocation = "external"
                        SharedPreferencesUtil.putString("cache_location", "external")
                        // Prompt for permissions if setting to external
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                storagePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        } else {
                            if (!ShizukuUtils.hasManageExternalStoragePermission(context) && !ShizukuUtils.isShizukuAuthorized()) {
                                showShizukuDialog = true
                            } else if (!ShizukuUtils.hasManageExternalStoragePermission(context) && ShizukuUtils.isShizukuAuthorized()) {
                                ShizukuUtils.grantManageExternalStorage(context)
                            }
                        }
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (cacheLocation == "external") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("外部存储 (Movies)")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    colors = if (cacheLocation == "external") {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "公共目录 (需授权全部文件访问权限, 卸载不会丢失)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (!hasPermission) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                storagePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            } else {
                                showShizukuDialog = true
                            }
                        }
                    },
                    colors = if (hasPermission){
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    else{
                        ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )},
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "所有文件访问权限", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (hasPermission) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = "Granted", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun SettingPlayerChooseScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var currentPlayer by remember { mutableStateOf(SharedPreferencesUtil.getString("player", "apsisPlayer")) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        , rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "默认播放器", color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                TitleCard(
                    onClick = {
                        currentPlayer = "apsisPlayer"
                        SharedPreferencesUtil.putString("player", "apsisPlayer")
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentPlayer == "apsisPlayer") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apsis Player")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    colors = if (currentPlayer == "apsisPlayer") {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "Orbit 内置的播放引擎，支持播放器/弹幕引擎双模切换，支持进度上报等独占功能",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            item {
                TitleCard(
                    onClick = {
                        currentPlayer = "pureAudioPlayer"
                        SharedPreferencesUtil.putString("player", "pureAudioPlayer")
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentPlayer == "pureAudioPlayer") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apsis AudioPlayer")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    colors = if (currentPlayer == "pureAudioPlayer") {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "Orbit 内置的专为听视频设计的音频播放引擎，界面采用原生风格，更加接近音乐软件",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                TitleCard(
                    onClick = {
                        currentPlayer = "aliangPlayer"
                        SharedPreferencesUtil.putString("player", "aliangPlayer")
                    },
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentPlayer == "aliangPlayer") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("凉腕播放器")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec),
                    colors = if (currentPlayer == "aliangPlayer") {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else CardDefaults.cardColors()
                ) {
                    Text(
                        "另一个优秀的第三方手表视频播放器，首创缩放视频功能，功能更加丰富",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                val qualityOptions = listOf(
                    16 to "360p",
                    32 to "480p",
                    64 to "720p",
                    80 to "1080p"
                )
                val currentQn = SharedPreferencesUtil.getInt("play_qn", 16)
                val currentIndex = qualityOptions.indexOfFirst { it.first == currentQn }.coerceAtLeast(0)
                val label = qualityOptions[currentIndex].second

                Button(
                    onClick = { navController.navigate("settings_video_quality") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "视频清晰度: $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {Spacer(Modifier.height(24.dp))}
        }
    }
}

@Composable
fun SettingVideoQualityScreen(navController: NavController) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalScreenRound.current
    var currentQn by remember { mutableIntStateOf(SharedPreferencesUtil.getInt("play_qn", 16)) }
    val isLoggedIn = CookieManager.getCookie().contains("SESSDATA")
    val context = LocalContext.current

    val qualityOptions = listOf(
        Triple(16, "360p", "流畅"),
        Triple(32, "480p", "清晰"),
        Triple(64, "720p", "高清 (需要登录)"),
        Triple(80, "1080p", "超清 (需要登录)")
    )

    ScreenScaffold(
        scrollState = listState,
        timeText = { WysTimeText() }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            rotaryScrollableBehavior = rememberSafeRotaryScrollableBehavior(listState)
        ) {
            item {
                ListHeader(
                    transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveTransformedHeight(this, transformationSpec)
                ) {
                    Text(text = "视频清晰度", color = MaterialTheme.colorScheme.primary)
                }
            }

            qualityOptions.forEach { option ->
                item {
                    val (qn, label, desc) = option
                    val isLocked = qn >= 64 && !isLoggedIn

                    Button(
                        onClick = {
                            if (isLocked) {
                                RoundToast.show(context, "请先登录以解锁更高清晰度")
                            } else {
                                currentQn = qn
                                SharedPreferencesUtil.putInt("play_qn", qn)
                            }
                        },
                        transformation = if (isRound) SurfaceTransformation(transformationSpec) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveTransformedHeight(this, transformationSpec),
                        colors = if (currentQn == qn) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = label, 
                                    color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (currentQn == qn) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
