package com.qx.orbit.bili.util.danmaku.base

import android.view.View
import android.content.Context
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.danmaku.dfm.DFMPlayer
import com.qx.orbit.bili.util.danmaku.dfmnext.DFMNextPlayer

interface DanmakuPlayer {
    val view: View
    fun prepare(parser: DanmakuParser, config: DanmakuConfig)
    fun start(ms: Long = 0L)
    fun resume()
    fun pause()
    fun seekTo(ms: Long)
    fun show()
    fun hide()
    fun release()
    fun addDanmaku(item: DanmakuItem)
    fun removeAllDanmakus(clearOnScreen: Boolean)
    fun enableDrawingCache(enable: Boolean)
    fun getCurrentTime(): Long
    fun setSpeed(speed: Float)
    fun isReady(): Boolean
    fun setOnReadyListener(listener: (() -> Unit)?)
}

fun createDanmakuPlayer(context: Context): DanmakuPlayer =
    if (SharedPreferencesUtil.getString("danmaku_engine", "dfm") == "dfm")
        DFMPlayer(context) else DFMNextPlayer(context)
