package com.qx.orbit.bili.util.danmaku.base

import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.danmaku.dfm.DFMConfig
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextConfig

interface DanmakuConfig {
    fun setDuplicateMerging(enable: Boolean)
    fun setSpecialDanmakuVisibility(enable: Boolean)
    fun preventOverlapping(overlappingPairs: Map<Int, Boolean>)
    fun setMaximumLines(maxLinesPair: Map<Int, Int>)
    fun setDanmakuTransparency(alpha: Float)
    fun setScaleTextSize(scale: Float)
}

fun createDanmakuConfig(): DanmakuConfig =
    if (SharedPreferencesUtil.getString("danmaku_engine", "dfm") == "dfm")
        DFMConfig() else DFMNextConfig()
