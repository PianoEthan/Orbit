package com.qx.orbit.bili.util.danmaku.dfm

import android.content.Context
import android.view.View
import com.qx.orbit.bili.util.danmaku.base.DanmakuConfig
import com.qx.orbit.bili.util.danmaku.base.DanmakuItem
import com.qx.orbit.bili.util.danmaku.base.DanmakuParser
import com.qx.orbit.bili.util.danmaku.base.DanmakuPlayer

class DFMPlayer(context: Context) : DanmakuPlayer {
    private val engine = master.flame.danmaku.ui.widget.DanmakuView(context)

    override val view: View get() = engine

    override fun prepare(parser: DanmakuParser, config: DanmakuConfig) {
        val engineParser = when (parser) {
            is DFMParser -> parser.engine
            is DFMProtobufParser -> parser.engine
            else -> throw IllegalArgumentException("Unsupported parser type")
        }
        engine.prepare(engineParser, (config as DFMConfig).engine)
    }

    override fun start() = engine.start()
    override fun resume() = engine.resume()
    override fun pause() = engine.pause()
    override fun seekTo(ms: Long) { engine.seekTo(ms) }
    override fun show() = engine.show()
    override fun hide() = engine.hide()
    override fun release() = engine.release()
    override fun addDanmaku(item: DanmakuItem) { engine.addDanmaku((item as DFMItem).engine) }
    override fun removeAllDanmakus(clearOnScreen: Boolean) = engine.removeAllDanmakus(clearOnScreen)
    override fun enableDrawingCache(enable: Boolean) = engine.enableDanmakuDrawingCache(enable)
    override fun getCurrentTime(): Long = engine.getCurrentTime()
    override fun setSpeed(speed: Float) = engine.setSpeed(speed)
}
