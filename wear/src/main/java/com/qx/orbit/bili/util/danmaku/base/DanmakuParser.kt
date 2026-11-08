package com.qx.orbit.bili.util.danmaku.base

import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.danmaku.dfm.DFMParser
import com.qx.orbit.bili.util.danmaku.dfm.DFMProtobufParser
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextParser
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextProtobufParser
import java.io.InputStream

interface DanmakuParser {
    fun load(inputStream: InputStream)
}

interface ProtobufDanmakuParser : DanmakuParser {
    fun setDanmakuSegments(segments: List<*>)
}

fun createProtobufParser(): ProtobufDanmakuParser =
    if (SharedPreferencesUtil.getString("danmaku_engine", "dfm") == "dfm")
        DFMProtobufParser() else DFMNextProtobufParser()

fun createXmlParser(): DanmakuParser =
    if (SharedPreferencesUtil.getString("danmaku_engine", "dfm") == "dfm")
        DFMParser() else DFMNextParser()

fun createEmptyParser(): DanmakuParser =
    if (SharedPreferencesUtil.getString("danmaku_engine", "dfm") == "dfm")
        DFMParser(empty = true) else DFMNextParser(empty = true)
