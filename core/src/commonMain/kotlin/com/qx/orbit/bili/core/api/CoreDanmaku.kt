package com.qx.orbit.bili.core.api

data class CoreDanmakuColor(val label: String, val value: Int)
data class CoreDanmakuMode(val label: String, val value: Int)

object CoreDanmaku {
    const val MAX_LENGTH = 100
    const val MODE_SCROLL = 1
    const val MODE_TOP = 4
    const val MODE_BOTTOM = 5

    val COLORS = listOf(
        CoreDanmakuColor("白", 16777215),
        CoreDanmakuColor("红", 16711680),
        CoreDanmakuColor("蓝", 255),
        CoreDanmakuColor("绿", 65280),
        CoreDanmakuColor("黄", 16776960),
        CoreDanmakuColor("紫", 16711935),
    )

    val MODES = listOf(
        CoreDanmakuMode("滚动", MODE_SCROLL),
        CoreDanmakuMode("顶部", MODE_TOP),
        CoreDanmakuMode("底部", MODE_BOTTOM),
    )
}
