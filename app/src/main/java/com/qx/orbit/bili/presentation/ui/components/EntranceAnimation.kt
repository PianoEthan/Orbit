package com.qx.orbit.bili.presentation.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 进入页面时的"从上到下渐显"：按 [index] 依次延迟淡入，并带一次轻微上移。
 *
 * 只作用于绘制层（alpha / translationY），不改变布局尺寸，因此 LazyColumn 项不会跳动。
 * [enabled] 只在首次组合时生效：滚出屏幕后才首次组合的项应传 false，直接以最终状态出现。
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    durationMillis: Int = 260,
    stepMillis: Int = 45,
    maxStaggerSteps: Int = 6,
    offsetDp: Float = 12f
): Modifier {
    val offsetPx = with(LocalDensity.current) { offsetDp.dp.toPx() }
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay((index.coerceIn(0, maxStaggerSteps) * stepMillis).toLong())
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
            )
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

/**
 * 入场窗口：在 [trigger] 首次变为 true 后返回 true，持续 [windowMillis] 毫秒。
 *
 * 用于决定哪些元素参与入场动画；已播放过不会因数据增量重播。
 * 系统"移除动画"（动画缩放为 0）时始终返回 false。
 */
@Composable
fun rememberEntranceWindow(trigger: Boolean, windowMillis: Long = 650L): Boolean {
    val context = LocalContext.current
    val animationsEnabled = remember(context) { areSystemAnimationsEnabled(context) }
    var windowClosed by remember { mutableStateOf(false) }

    // 同步判定：trigger 首次为 true 的当帧就返回 true。
    // 若放到 LaunchedEffect 里再翻转，同一帧完成组合的 Lazy 子项会拿到 enabled = false，
    // 并把 Animatable 冻结在 1f，入场动画会静默失效。
    val active = trigger && animationsEnabled && !windowClosed

    LaunchedEffect(active) {
        if (active) {
            delay(windowMillis)
            windowClosed = true
        }
    }
    return active
}

private fun areSystemAnimationsEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    } catch (e: Exception) {
        true
    }
}
