package com.qx.orbit.bili.util.danmaku.base

import com.qx.orbit.bili.util.danmaku.dfm.DFMConfig
import com.qx.orbit.bili.util.danmaku.dfm.DFMItem
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextConfig
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextItem

interface DanmakuItem {
    var text: CharSequence?
    var textColor: Int
    var textSize: Float
    var time: Long
    var padding: Int
    var priority: Int
    var borderColor: Int
    var userHash: String
    var obj: Any?
}

fun createDanmaku(config: DanmakuConfig, type: Int): DanmakuItem? {
    return when (config) {
        is DFMNextConfig -> config.mDanmakuFactory.createDanmaku(type)?.let { DFMNextItem(it) }
        is DFMConfig -> config.mDanmakuFactory.createDanmaku(type)?.let { DFMItem(it) }
        else -> null
    }
}
