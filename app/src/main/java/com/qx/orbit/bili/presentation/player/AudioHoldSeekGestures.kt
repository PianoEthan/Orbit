package com.qx.orbit.bili.presentation.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max

/**
 * 「按住快进/快退」手势回调。
 *
 * 状态机：
 * - 抬起早于长按阈值且位移不超过 touchSlop → [onTap]（音频页仍用于双击退出）
 * - 位移超过 touchSlop 而尚未达到阈值 → 整个手势放弃，不触发任何回调
 * - 按住达到阈值 → [onHoldStart]，按下点位于屏幕左半侧为快退；抬手时 [onHoldCommit]
 * - 按住期间第二根手指按下 / 手势被打断 → [onHoldCancel]，不提交 seek
 */
internal data class AudioHoldSeekCallbacks(
    val enabled: Boolean = false,
    val onTap: (() -> Unit)? = null,
    val onHoldStart: (rewind: Boolean) -> Unit = {},
    val onHoldCancel: () -> Unit = {},
    val onHoldCommit: () -> Unit = {}
)

/**
 * 挂在“非按钮”区域上的长按快进手势（标题条、背景空白层）。
 *
 * 只在未被其它点击组件消费的按下事件上启动，因此不会抢走播放/暂停等按钮的点击，
 * 也不会抢走 Horologist 内部的按钮涟漪。
 */
@Composable
internal fun Modifier.audioHoldSeekGestures(callbacks: AudioHoldSeekCallbacks): Modifier {
    val latest by rememberUpdatedState(callbacks)
    return pointerInput(Unit) {
        awaitEachGesture {
            var holding = false
            var committed = false
            try {
                val down = awaitFirstDown(requireUnconsumed = true)
                var travelled = 0f
                val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                while (true) {
                    val waitingForHold = !holding
                    val remaining = if (waitingForHold) {
                        deadline - (currentEvent.changes.lastOrNull()?.uptimeMillis ?: down.uptimeMillis)
                    } else {
                        Long.MAX_VALUE
                    }
                    val event = if (waitingForHold) {
                        // 超时即视为“按住”：手指静止时不会再有移动事件，不能一直 await 下去
                        if (remaining <= 0L) null else withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        // 到达长按阈值仍未抬手：进入按住快进/快退（左半侧快退，右半侧快进）
                        if (!latest.enabled) return@awaitEachGesture
                        holding = true
                        latest.onHoldStart(down.position.x < size.width / 2f)
                        continue
                    }
                    if (!holding && event.changes.any { it.isConsumed }) return@awaitEachGesture
                    val pressedChanges = event.changes.filter { it.pressed }
                    if (pressedChanges.size > 1) {
                        // 第二根手指：取消，不落位
                        return@awaitEachGesture
                    }
                    val pointer = pressedChanges.firstOrNull()
                    if (pointer == null) {
                        val up = event.changes.lastOrNull { it.id == down.id }
                        if (holding) {
                            latest.onHoldCommit()
                            committed = true
                        } else if (up != null && travelled <= viewConfiguration.touchSlop &&
                            up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                        ) {
                            latest.onTap?.invoke()
                        }
                        return@awaitEachGesture
                    }
                    if (pointer.id == down.id) {
                        travelled = max(travelled, (pointer.position - down.position).getDistance())
                        // 还没进入按住状态就滑走了：交给系统滚动/返回手势，不抢
                        if (!holding && travelled > viewConfiguration.touchSlop) return@awaitEachGesture
                    }
                }
            } finally {
                if (holding && !committed) latest.onHoldCancel()
            }
        }
    }
}
