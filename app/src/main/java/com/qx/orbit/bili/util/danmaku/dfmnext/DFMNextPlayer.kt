package com.qx.orbit.bili.util.danmaku.dfmnext

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.qx.orbit.bili.util.danmaku.base.DanmakuConfig
import com.qx.orbit.bili.util.danmaku.base.DanmakuItem
import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.DanmakuPlayer

class DFMNextPlayer(context: Context) : DanmakuPlayer {
    private val engine = io.github.ynotbili.dfmnext.ui.widget.DanmakuView(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyListener: (() -> Unit)? = null
    private var ready = false

    override val view: View get() = engine

    override fun prepare(parser: DanmakuParser, config: DanmakuConfig) {
        val engineParser = when (parser) {
            is DFMNextParser -> parser.engine
            is DFMNextProtobufParser -> parser.engine
            else -> throw IllegalArgumentException("Unsupported parser type")
        }
        engine.setCallback(object : io.github.ynotbili.dfmnext.controller.DrawHandler.Callback {
            override fun prepared() {
                ready = true
                val listener = readyListener
                if (listener != null) {
                    mainHandler.post { listener.invoke() }
                }
            }
            override fun updateTimer(timer: io.github.ynotbili.dfmnext.danmaku.model.DanmakuTimer) {}
            override fun danmakuShown(danmaku: io.github.ynotbili.dfmnext.danmaku.model.BaseDanmaku) {}
            override fun drawingFinished() {}
        })
        engine.prepare(engineParser, (config as DFMNextConfig).engine)
    }
    override fun start(ms: Long) = engine.start(ms)
    override fun resume() = engine.resume()
    override fun pause() = engine.pause()
    override fun seekTo(ms: Long) { engine.seekTo(ms) }
    override fun show() = engine.show()
    override fun hide() = engine.hide()
    override fun release() {
        ready = false
        readyListener = null
        try {
            engine.stop()
            engine.removeAllDanmakus(true)
            engine.enableDanmakuDrawingCache(false)
        } catch (_: Exception) {}
        engine.release()
    }
    override fun addDanmaku(item: DanmakuItem) { engine.addDanmaku((item as DFMNextItem).engine) }
    override fun removeAllDanmakus(clearOnScreen: Boolean) = engine.removeAllDanmakus(clearOnScreen)
    override fun enableDrawingCache(enable: Boolean) = engine.enableDanmakuDrawingCache(enable)
    override fun getCurrentTime(): Long = engine.getCurrentTime()
    override fun setSpeed(speed: Float) = engine.setSpeed(speed)
    override fun isReady(): Boolean = ready
    override fun setOnReadyListener(listener: (() -> Unit)?) { readyListener = listener }
}
