package com.qx.orbit.bili.vision

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class VisionSettingsSection(val title: String, val description: String, val icon: ImageVector) {
    APPEARANCE("外观与主题", "深色、浅色、动态配色", Icons.Default.Palette),
    PLAYBACK("播放与控制", "播放器内核、弹幕与控制栏", Icons.Default.PlayCircle),
    INPUT("输入与遥控器", "九键输入与按键说明", Icons.Default.Keyboard),
    ABOUT("关于 Orbit Vision", "版本与应用信息", Icons.Default.Info),
}

@Composable
internal fun VisionSettingsScreen(onBack: () -> Unit, onOpenSection: (VisionSettingsSection) -> Unit) {
    SettingsPage(onBack) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("偏好", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        VisionSettingsSection.entries.take(3).forEachIndexed { index, section ->
            SettingsEntry(section, index, 3) { onOpenSection(section) }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("应用", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        SettingsEntry(VisionSettingsSection.ABOUT, 0, 1) { onOpenSection(VisionSettingsSection.ABOUT) }
    }
}

@Composable
private fun SettingsEntry(section: VisionSettingsSection, index: Int, count: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(
        topStart = if (index == 0) 24.dp else 4.dp, topEnd = if (index == 0) 24.dp else 4.dp,
        bottomStart = if (index == count - 1) 24.dp else 4.dp, bottomEnd = if (index == count - 1) 24.dp else 4.dp,
    )
    Surface(
        onClick = onClick, interactionSource = interaction, shape = shape,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(section.icon, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                Text(section.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
internal fun VisionSettingsSectionScreen(section: VisionSettingsSection, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(onBack) {
        Text(section.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        when (section) {
            VisionSettingsSection.APPEARANCE -> {
                Text("主题模式", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    VisionThemeMode.entries.forEach { mode ->
                        SettingsRadio(mode.title, VisionPreferences.themeMode == mode) { VisionPreferences.setThemeMode(context, mode) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(24.dp))
                    SettingsToggle("动态配色", "使用系统壁纸配色", VisionPreferences.dynamicColor) { VisionPreferences.setDynamicColor(context, it) }
                }
            }
            VisionSettingsSection.PLAYBACK -> {
                Text("播放器内核", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    VisionPlayerEngine.entries.forEach { engine ->
                        SettingsRadio(engine.title, VisionPreferences.playerEngine == engine) { VisionPreferences.setPlayerEngine(context, engine) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("弹幕显示设置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                SettingsToggle("启用弹幕", "播放视频时默认展示弹幕（使用 :DFMNext 引擎）", VisionPreferences.danmakuEnabled) { VisionPreferences.setDanmakuEnabled(context, it) }
                Spacer(Modifier.height(16.dp))
                Text("弹幕透明度", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    listOf(1.0f to "不透明 (100%)", 0.85f to "适中 (85%)", 0.6f to "半透明 (60%)").forEach { (alpha, title) ->
                        SettingsRadio(title, VisionPreferences.danmakuAlpha == alpha) { VisionPreferences.setDanmakuAlpha(context, alpha) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("弹幕大小", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    listOf(0.8f to "小", 1.0f to "标准 (推荐)", 1.25f to "大 (适合远距离电视)").forEach { (scale, title) ->
                        SettingsRadio(title, VisionPreferences.danmakuTextScale == scale) { VisionPreferences.setDanmakuTextScale(context, scale) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("控制栏自动隐藏", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup()) {
                    listOf(4500L to "4.5 秒", 8000L to "8 秒", 0L to "不自动隐藏").forEach { (timeout, title) ->
                        SettingsRadio(title, VisionPreferences.controlsTimeoutMs == timeout) { VisionPreferences.setControlsTimeout(context, timeout) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("暂停、缓冲、调整进度或播放异常时不会自动隐藏。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VisionSettingsSection.INPUT -> {
                SettingsToggle("默认使用九键输入", "电视上始终优先使用九键，手机也可手动切换。", VisionPreferences.preferNineKey) { VisionPreferences.setPreferNineKey(context, it) }
                Spacer(Modifier.height(24.dp))
                Text("遥控器与键盘", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text("方向键：移动焦点\n确认键：打开字母组\n字母组中：上、右、下、左分别选择对应字母\n返回键：关闭字母组或返回上一层\n播放器控件隐藏时：左右键快退 / 快进 10 秒\n播放 / 暂停键或空格：控制播放", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text("九键提供字母、数字及符号输入；中文请切换系统输入法。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VisionSettingsSection.ABOUT -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(72.dp)) {
                        Image(painterResource(R.mipmap.ic_launcher_background), null, Modifier.fillMaxSize())
                        Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
                    }
                    Column {
                        Text("Orbit Vision", style = MaterialTheme.typography.titleLarge)
                        Text("0.1.0 · 手机 / 平板 / 电视", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("与 Orbit Wear 共享核心接口，使用标准 Material 3 Expressive。", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val focus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        if (inputMode.inputMode == InputMode.Keyboard) { withFrameNanos { }; focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).focusRequester(focus)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsRadio(title: String, selected: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onSelect,
        interactionSource = interaction,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer
            else if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioButton(selected, onClick = null)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = { onChange(!checked) },
        interactionSource = interaction,
        color = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = if (focused) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = null)
        }
    }
}
