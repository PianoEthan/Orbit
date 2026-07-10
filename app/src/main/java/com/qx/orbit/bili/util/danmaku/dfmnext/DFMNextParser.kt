package com.qx.orbit.bili.util.danmaku.dfmnext

import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.ProtobufDanmakuParser
import java.io.InputStream

class DFMNextParser(empty: Boolean = false) : DanmakuParser {
    internal val engine: rj.dfmnext.danmaku.parser.BaseDanmakuParser =
        if (empty) object : rj.dfmnext.danmaku.parser.BaseDanmakuParser() {
            override fun parse() = rj.dfmnext.danmaku.model.android.Danmakus()
        } else rj.dfmnext.danmaku.parser.android.BiliDanmukuParser()

    override fun load(inputStream: InputStream) {
        val source = rj.dfmnext.danmaku.parser.android.AndroidFileSource(inputStream)
        engine.load(source)
    }
}

class DFMNextProtobufParser : ProtobufDanmakuParser {
    internal val engine = rj.dfmnext.danmaku.parser.android.BiliProtobufDanmakuParser()

    override fun setDanmakuSegments(segments: List<*>) {
        engine.setDanmakuSegments(segments)
    }

    override fun load(inputStream: InputStream) {}
}
