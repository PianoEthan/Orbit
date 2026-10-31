package com.qx.orbit.bili.util.danmaku.dfm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.qx.orbit.bili.util.danmaku.base.DanmakuConfig
import com.qx.orbit.bili.util.danmaku.base.DanmakuItem
import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.DanmakuPlayer
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.ui.widget.DanmakuView

class DFMPlayer(context: Context) : DanmakuPlayer {
    private val engine = DanmakuView(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyListener: (() -> Unit)? = null
    @Volatile
    private var ready = false
    @Volatile
    private var released = false
    @Volatile
    private var prepareGeneration = 0
    private var hasPrepared = false

    override val view: View get() = engine

    override fun prepare(parser: DanmakuParser, config: DanmakuConfig) {
        if (released) return
        val engineParser = when (parser) {
            is DFMParser -> parser.engine
            is DFMProtobufParser -> parser.engine
            else -> throw IllegalArgumentException("Unsupported parser type")
        }
        val engineConfig = (config as DFMConfig).engine
        val generation = ++prepareGeneration
        ready = false
        mainHandler.removeCallbacksAndMessages(null)
        if (hasPrepared) engine.stop()
        hasPrepared = true
        engine.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                mainHandler.post {
                    if (!released && generation == prepareGeneration) {
                        ready = true
                        readyListener?.invoke()
                    }
                }
            }
            override fun updateTimer(timer: DanmakuTimer?) {}
            override fun danmakuShown(danmaku: BaseDanmaku?) {}
            override fun drawingFinished() {}
        })
        engine.prepare(engineParser, engineConfig)
    }
    override fun start(ms: Long) = engine.start(ms)
    override fun resume() = engine.resume()
    override fun pause() = engine.pause()
    override fun seekTo(ms: Long) { engine.seekTo(ms) }
    override fun show() = engine.show()
    override fun hide() = engine.hide()
    override fun release() {
        released = true
        prepareGeneration++
        ready = false
        readyListener = null
        mainHandler.removeCallbacksAndMessages(null)
        engine.setCallback(null)
        try {
            engine.stop()
            engine.removeAllDanmakus(true)
            engine.enableDanmakuDrawingCache(false)
        } catch (_: Exception) {}
        try {
            engine.release()
        } catch (_: Exception) {}
    }
    override fun addDanmaku(item: DanmakuItem) {
        if (!ready || released) return
        try {
            engine.addDanmaku((item as DFMItem).engine)
        } catch (_: Exception) {}
    }
    override fun removeAllDanmakus(clearOnScreen: Boolean) = engine.removeAllDanmakus(clearOnScreen)
    override fun enableDrawingCache(enable: Boolean) = engine.enableDanmakuDrawingCache(enable)
    override fun getCurrentTime(): Long = engine.getCurrentTime()
    override fun setSpeed(speed: Float) = engine.setSpeed(speed)
    override fun isReady(): Boolean = ready
    override fun setOnReadyListener(listener: (() -> Unit)?) { readyListener = listener }
}
