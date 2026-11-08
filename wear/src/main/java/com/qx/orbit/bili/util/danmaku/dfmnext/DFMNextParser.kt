package com.qx.orbit.bili.util.danmaku.dfmnext

import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.ProtobufDanmakuParser
import java.io.InputStream

class DFMNextParser(empty: Boolean = false) : DanmakuParser {
    internal val engine: io.github.ynotbili.dfmnext.danmaku.parser.BaseDanmakuParser =
        if (empty) object : io.github.ynotbili.dfmnext.danmaku.parser.BaseDanmakuParser() {
            override fun parse() = io.github.ynotbili.dfmnext.danmaku.model.android.Danmakus()
        } else io.github.ynotbili.dfmnext.danmaku.parser.android.BiliDanmukuParser()

    override fun load(inputStream: InputStream) {
        val source = io.github.ynotbili.dfmnext.danmaku.parser.android.AndroidFileSource(inputStream)
        engine.load(source)
    }
}

class DFMNextProtobufParser : ProtobufDanmakuParser {
    internal val engine = io.github.ynotbili.dfmnext.danmaku.parser.android.BiliProtobufDanmakuParser()

    override fun setDanmakuSegments(segments: List<*>) {
        engine.setDanmakuSegments(segments)
    }

    override fun load(inputStream: InputStream) {}
}
