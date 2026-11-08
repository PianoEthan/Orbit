package com.qx.orbit.bili.util.danmaku.dfm

import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.ProtobufDanmakuParser
import java.io.InputStream

class DFMParser(empty: Boolean = false) : DanmakuParser {
    internal val engine: master.flame.danmaku.danmaku.parser.BaseDanmakuParser =
        if (empty) object : master.flame.danmaku.danmaku.parser.BaseDanmakuParser() {
            override fun parse() = master.flame.danmaku.danmaku.model.android.Danmakus()
        } else master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser()

    override fun load(inputStream: InputStream) {
        val loader = master.flame.danmaku.danmaku.loader.android.BiliDanmakuLoader.instance()
        loader.load(inputStream)
        engine.load(loader.dataSource)
    }
}

class DFMProtobufParser : ProtobufDanmakuParser {
    internal val engine = master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser()

    override fun setDanmakuSegments(segments: List<*>) {
        engine.setDanmakuSegments(segments)
    }

    override fun load(inputStream: InputStream) {}
}
