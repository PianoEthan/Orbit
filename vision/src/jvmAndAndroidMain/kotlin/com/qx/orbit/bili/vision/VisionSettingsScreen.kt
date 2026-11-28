@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.qx.orbit.bili.vision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInputModeManager
import org.jetbrains.compose.resources.painterResource
import com.qx.orbit.bili.core.api.OrbitContributorId
import com.qx.orbit.bili.core.api.orbitContributors
import com.qx.orbit.bili.vision.resources.Res
import com.qx.orbit.bili.vision.resources.contributor_iam_rj
import com.qx.orbit.bili.vision.resources.contributor_qinxian
import com.qx.orbit.bili.vision.resources.vision_launcher_background
import com.qx.orbit.bili.vision.resources.vision_launcher_foreground
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class VisionSettingsSection(val title: String, val description: String, val icon: ImageVector) {
    APPEARANCE("外观与主题", "深色、浅色、动态配色", Icons.Default.Palette),
    PLAYBACK("播放与控制", "播放器内核、弹幕与控制栏", Icons.Default.PlayCircle),
    INPUT("输入与遥控器", "九键输入与按键说明", Icons.Default.Keyboard),
    ABOUT("关于 Orbit Vision", "版本与应用信息", Icons.Default.Info),
}

@Composable
internal fun VisionSettingsScreen(onBack: () -> Unit, onOpenSection: (VisionSettingsSection) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            SplitSettingsPage(onBack, (maxWidth * 0.32f).coerceIn(224.dp, 288.dp))
        } else {
            CompactSettingsPage(onBack, onOpenSection)
        }
    }
}

@Composable
private fun SplitSettingsPage(onBack: () -> Unit, categoryWidth: Dp) {
    var section by rememberSaveable { mutableStateOf(VisionSettingsSection.APPEARANCE) }
    val backFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    VisionInitialFocus(backFocus)
    Row(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Surface(
            Modifier.width(categoryWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsBackButton(onBack, Modifier.focusRequester(backFocus))
                    Text("设置", style = MaterialTheme.typography.headlineSmall)
                }
                Column(
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    VisionSettingsSection.entries.forEachIndexed { index, entry ->
                        SettingsCategoryItem(entry, index, selected = entry == section) { section = entry }
                    }
                }
            }
        }
        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Text(
                    section.title,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
                key(section) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        SettingsSectionContent(section)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSettingsPage(onBack: () -> Unit, onOpenSection: (VisionSettingsSection) -> Unit) {
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
private fun SettingsCategoryItem(
    section: VisionSettingsSection,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shapes = ListItemDefaults.segmentedShapes(index, VisionSettingsSection.entries.size)
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = Modifier.settingsFocus(shapes, interaction).heightIn(min = 72.dp)
            .semantics { role = Role.Tab },
        interactionSource = interaction,
        colors = settingsItemColors(),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        leadingContent = {
            Surface(
                Modifier.size(40.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(section.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        supportingContent = { Text(section.description) },
    ) {
        Text(section.title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsEntry(
    section: VisionSettingsSection,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
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
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
    SettingsPage(onBack, title = section.title) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsSectionContent(section)
        }
    }
}

@Composable
private fun SettingsSectionContent(section: VisionSettingsSection) {
    when (section) {
        VisionSettingsSection.APPEARANCE -> {
            SettingsChoices(
                title = "主题模式",
                options = VisionThemeMode.entries.map { SettingsOption(it, it.title) },
                selected = VisionPreferences.themeMode,
                onSelect = VisionPreferences::updateThemeMode,
            )
            if (visionDynamicColorsAvailable()) {
                SettingsToggle(
                    "动态配色", "使用系统壁纸配色", VisionPreferences.dynamicColor,
                    ListItemDefaults.segmentedShapes(0, 1), VisionPreferences::updateDynamicColor,
                )
            }
        }
        VisionSettingsSection.PLAYBACK -> PlaybackSettings()
        VisionSettingsSection.INPUT -> InputSettings()
        VisionSettingsSection.ABOUT -> AboutSettings()
    }
}

@Composable
private fun PlaybackSettings() {
    val loopPlayback = VisionPreferences.loopPlayback
    val danmakuEnabled = VisionPreferences.danmakuEnabled
    val engineOptions = platformVisionEngines.map { engine ->
        val available = isVisionEngineAvailable(engine)
        SettingsOption(
            value = engine,
            title = engine.title,
            supporting = if (available) null else "需要 LibVLC 3",
            enabled = available,
        )
    }
    SettingsChoices(
        title = "播放器内核",
        options = engineOptions,
        selected = VisionPreferences.playerEngine.takeIf(::isVisionEngineAvailable) ?: platformVisionEngines.first(),
        onSelect = VisionPreferences::updatePlayerEngine,
    )
    SettingsGroup("播放行为") {
        SettingsToggle(
            "循环播放", "当前视频播放结束后从头播放，优先于自动下一 P。", loopPlayback,
            ListItemDefaults.segmentedShapes(0, 3), VisionPreferences::updateLoopPlayback,
        )
        SettingsToggle(
            "自动播放下一 P",
            if (loopPlayback) "关闭循环播放后可用。" else "当前分 P 播放结束后继续下一 P。",
            VisionPreferences.autoNextPart,
            ListItemDefaults.segmentedShapes(1, 3),
            VisionPreferences::updateAutoNextPart,
            enabled = !loopPlayback,
        )
        SettingsToggle(
            "视频缩放与平移", "用双指手势或播放器里的缩放按钮调整画面。", VisionPreferences.videoZoomEnabled,
            ListItemDefaults.segmentedShapes(2, 3), VisionPreferences::updateVideoZoomEnabled,
        )
    }
    SettingsGroup("弹幕") {
        SettingsToggle(
            "启用弹幕", "播放视频时默认展示弹幕", danmakuEnabled,
            ListItemDefaults.segmentedShapes(0, 1), VisionPreferences::updateDanmakuEnabled,
        )
    }
    SettingsChoices(
        title = "弹幕透明度",
        options = listOf(SettingsOption(1f, "不透明 (100%)"), SettingsOption(0.85f, "适中 (85%)"), SettingsOption(0.6f, "半透明 (60%)")),
        selected = VisionPreferences.danmakuAlpha,
        onSelect = VisionPreferences::updateDanmakuAlpha,
        enabled = danmakuEnabled,
        supporting = if (danmakuEnabled) null else "开启弹幕后可调整透明度和大小。",
    )
    SettingsChoices(
        title = "弹幕大小",
        options = listOf(SettingsOption(0.8f, "小"), SettingsOption(1f, "标准 (推荐)"), SettingsOption(1.25f, "大 (适合远距离电视)")),
        selected = VisionPreferences.danmakuTextScale,
        onSelect = VisionPreferences::updateDanmakuTextScale,
        enabled = danmakuEnabled,
    )
    SettingsChoices(
        title = "控制栏自动隐藏",
        options = listOf(SettingsOption(4500L, "4.5 秒"), SettingsOption(8000L, "8 秒"), SettingsOption(0L, "不自动隐藏")),
        selected = VisionPreferences.controlsTimeoutMs,
        onSelect = VisionPreferences::updateControlsTimeout,
        supporting = "暂停、缓冲、调整进度或播放异常时不会自动隐藏。",
    )
}

@Composable
private fun InputSettings() {
    SettingsToggle(
        "默认使用九键输入", "电视上始终优先使用九键，手机也可手动切换。", VisionPreferences.preferNineKey,
        ListItemDefaults.segmentedShapes(0, 1), VisionPreferences::updatePreferNineKey,
    )
    SettingsGroup("遥控器与键盘", "九键支持字母、数字和符号。输入中文时请切换系统输入法。") {
        val shortcuts = listOf(
            "方向键" to "移动焦点",
            "确认键" to "打开字母组",
            "字母组内的方向键" to "选择对应字母",
            "返回键" to "关闭字母组或返回上一层",
            "播放器控件隐藏时按左右键" to "快退或快进 10 秒",
            "播放键、暂停键或空格" to "控制播放",
        )
        shortcuts.forEachIndexed { index, (key, action) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index, shortcuts.size),
                modifier = Modifier.fillMaxWidth(),
                colors = settingsItemColors(),
                supportingContent = { Text(action) },
            ) { Text(key) }
        }
    }
}

@Composable
private fun AboutSettings() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(painterResource(Res.drawable.vision_launcher_background), null, Modifier.fillMaxSize())
                    Image(painterResource(Res.drawable.vision_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Orbit Vision", style = MaterialTheme.typography.headlineSmall)
                    Text("0.1.0 · Android / Desktop", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("与 Orbit Wear 共享核心接口，使用标准 Material 3 Expressive。", style = MaterialTheme.typography.bodyMedium)
        }
    }

    SettingsGroup("开发人员名单") {
        orbitContributors.forEachIndexed { index, contributor ->
            val avatar = when (contributor.id) {
                OrbitContributorId.QINXIAN -> Res.drawable.contributor_qinxian
                OrbitContributorId.IAM_RJ -> Res.drawable.contributor_iam_rj
            }
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index, orbitContributors.size),
                modifier = Modifier.fillMaxWidth(),
                colors = settingsItemColors(),
                leadingContent = {
                    Image(
                        painter = painterResource(avatar),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                },
                supportingContent = { Text(contributor.role.label) },
            ) {
                Text(contributor.displayName)
            }
        }
    }
}

@Composable
private fun SettingsPage(
    onBack: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        if (inputMode.inputMode == InputMode.Keyboard) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    val scrollState = rememberScrollState()
    val scrollBehavior = if (title == null) null else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
    ) {
        if (title == null) {
            SettingsBackButton(onBack, Modifier.padding(8.dp).focusRequester(focus))
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LargeFlexibleTopAppBar(
                    title = { Text(title) },
                    navigationIcon = { SettingsBackButton(onBack, Modifier.focusRequester(focus)) },
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = if (title == null) 8.dp else 16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsBackButton(onBack: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onBack, modifier = modifier) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
}

@Composable
private fun SettingsGroup(
    title: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
        if (supporting != null) {
            Text(
                supporting,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class SettingsOption<T>(
    val value: T,
    val title: String,
    val supporting: String? = null,
    val enabled: Boolean = true,
)

@Composable
private fun <T> SettingsChoices(
    title: String,
    options: List<SettingsOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SettingsGroup(title, supporting, Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val shapes = ListItemDefaults.segmentedShapes(index, options.size)
            val interaction = remember { MutableInteractionSource() }
            val checked = option.value == selected
            val available = enabled && option.enabled
            SegmentedListItem(
                selected = checked,
                onClick = { onSelect(option.value) },
                shapes = shapes,
                modifier = Modifier.settingsFocus(shapes, interaction).semantics { role = Role.RadioButton },
                enabled = available,
                colors = settingsItemColors(),
                supportingContent = option.supporting?.let { supportingText -> { Text(supportingText) } },
                trailingContent = { RadioButton(checked, onClick = null, enabled = available) },
                interactionSource = interaction,
            ) { Text(option.title) }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    shapes: ListItemShapes,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        shapes = shapes,
        modifier = Modifier.settingsFocus(shapes, interaction).semantics { role = Role.Switch },
        colors = settingsItemColors(),
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
        interactionSource = interaction,
    ) { Text(title) }
}

@Composable
private fun settingsItemColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
private fun Modifier.settingsFocus(shapes: ListItemShapes, interaction: MutableInteractionSource): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val row = fillMaxWidth()
    return if (focused) row.border(2.dp, MaterialTheme.colorScheme.primary, shapes.focusedShape) else row
}
