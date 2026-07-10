package com.qx.orbit.bili.util.danmaku.dfm

import com.qx.orbit.bili.util.danmaku.base.DanmakuConfig

class DFMConfig : DanmakuConfig {
    internal val engine = master.flame.danmaku.danmaku.model.android.DanmakuContext.create()

    val mDanmakuFactory get() = engine.mDanmakuFactory

    override fun setDuplicateMerging(enable: Boolean) { engine.setDuplicateMergingEnabled(enable) }
    override fun setSpecialDanmakuVisibility(enable: Boolean) { engine.setSpecialDanmakuVisibility(enable) }
    override fun preventOverlapping(overlappingPairs: Map<Int, Boolean>) { engine.preventOverlapping(overlappingPairs) }
    override fun setMaximumLines(maxLinesPair: Map<Int, Int>) { engine.setMaximumLines(maxLinesPair) }
    override fun setDanmakuTransparency(alpha: Float) { engine.setDanmakuTransparency(alpha) }
    override fun setScaleTextSize(scale: Float) { engine.setScaleTextSize(scale) }
}
