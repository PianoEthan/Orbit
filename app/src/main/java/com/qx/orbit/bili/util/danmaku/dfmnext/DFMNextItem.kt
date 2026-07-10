package com.qx.orbit.bili.util.danmaku.dfmnext

import com.qx.orbit.bili.util.danmaku.base.DanmakuItem

class DFMNextItem(internal val engine: rj.dfmnext.danmaku.model.BaseDanmaku) : DanmakuItem {
    override var text: CharSequence?
        get() = engine.text
        set(v) { engine.text = v }

    override var textColor: Int
        get() = engine.textColor
        set(v) { engine.textColor = v }

    override var textSize: Float
        get() = engine.textSize
        set(v) { engine.textSize = v }

    override var time: Long
        get() = engine.time
        set(v) { engine.time = v }

    override var padding: Int
        get() = engine.padding
        set(v) { engine.padding = v }

    override var priority: Int
        get() = engine.priority.toInt()
        set(v) { engine.priority = v.toByte() }

    override var borderColor: Int
        get() = engine.borderColor
        set(v) { engine.borderColor = v }

    override var userHash: String
        get() = engine.userHash ?: ""
        set(v) { engine.userHash = v }

    override var obj: Any?
        get() = engine.obj
        set(v) { engine.obj = v }
}
