package com.qx.orbit.bili.vision

import android.os.Handler
import android.os.Looper
import com.qx.orbit.bili.core.api.DanmakuGateway
import com.qx.orbit.bili.core.api.decompressBilibiliDanmakuStream
import io.github.ynotbili.dfmnext.controller.DrawHandler
import io.github.ynotbili.dfmnext.danmaku.model.BaseDanmaku
import io.github.ynotbili.dfmnext.danmaku.model.DanmakuTimer
import io.github.ynotbili.dfmnext.danmaku.model.android.DanmakuContext
import io.github.ynotbili.dfmnext.danmaku.parser.android.AndroidFileSource
import io.github.ynotbili.dfmnext.danmaku.parser.android.BiliDanmukuParser
import io.github.ynotbili.dfmnext.ui.widget.DanmakuView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.abs

internal actual class VisionDanmakuHost actual constructor(private val gateway: DanmakuGateway) {
    private var view: DanmakuView? = null
    private var config: DanmakuContext? = null
    private var parser: BiliDanmukuParser? = null
    internal val isReady: Boolean get() = prepared
    internal val currentTime: Long get() = view?.getCurrentTime() ?: 0L
    private var prepared = false
    private var playing = false
    private var enabled = VisionPreferences.danmakuEnabled
    private var position = 0L
    private var speed = 1f
    private var loadJob: Job? = null
    private var currentCid = 0L
    private var generation = 0
    private var released = false
    private val handler = Handler(Looper.getMainLooper())

    fun bindView(view: DanmakuView) {
        this.view = view
        if (parser != null) prepareView()
    }

    actual fun load(cid: Long, scope: CoroutineScope) {
        if (released || cid <= 0L || (cid == currentCid && parser != null)) return
        currentCid = cid
        val token = ++generation
        loadJob?.cancel()
        view?.setCallback(null)
        view?.stop()
        parser?.release()
        parser = null
        prepared = false
        position = 0L
        loadJob = scope.launch {
            val bytes = gateway.loadXmlDanmaku(cid) ?: return@launch
            val stream = try {
                withContext(Dispatchers.Default) { decompressBilibiliDanmakuStream(bytes) }
            } catch (_: IOException) { return@launch }
            ensureActive()
            if (released || generation != token) { stream.close(); return@launch }
            parser = BiliDanmukuParser().apply { load(AndroidFileSource(stream)) }
            prepareView()
        }
    }

    private fun prepareView() {
        val target = view ?: return
        val source = parser ?: return
        val token = generation
        config = DanmakuContext.create().apply {
            setDanmakuTransparency(VisionPreferences.danmakuAlpha)
            setScaleTextSize(VisionPreferences.danmakuTextScale)
            setDuplicateMergingEnabled(true)
            preventOverlapping(mapOf(BaseDanmaku.TYPE_SCROLL_RL to true, BaseDanmaku.TYPE_FIX_TOP to true))
        }
        target.setCallback(object : DrawHandler.Callback {
            override fun prepared() {
                handler.post {
                    if (!released && generation == token) {
                        prepared = true
                        target.start(position)
                        target.setSpeed(speed)
                        if (!enabled) target.hide()
                        if (!playing || !enabled) target.pause()
                    }
                }
            }
            override fun updateTimer(timer: DanmakuTimer) = Unit
            override fun danmakuShown(danmaku: BaseDanmaku) = Unit
            override fun drawingFinished() = Unit
        })
        target.enableDanmakuDrawingCache(true)
        target.prepare(source, requireNotNull(config))
    }

    actual fun synchronize(positionMs: Long, playing: Boolean, speed: Float) {
        position = positionMs.coerceAtLeast(0)
        val target = view
        if (prepared && target != null) {
            if (abs(target.getCurrentTime() - position) > 1000L) target.seekTo(position)
            if (this.speed != speed) target.setSpeed(speed)
        }
        this.speed = speed
        setPlaying(playing)
    }

    actual fun setPlaying(playing: Boolean) {
        val changed = this.playing != playing
        this.playing = playing
        if (!changed || !prepared) return
        if (playing && enabled) view?.resume() else view?.pause()
    }

    actual fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!prepared) return
        if (enabled) {
            view?.seekTo(position)
            view?.show()
            if (playing) view?.resume() else view?.pause()
        } else {
            view?.hideAndPauseDrawTask()
        }
    }

    actual fun updateConfig(alpha: Float, textScale: Float) {
        config?.setDanmakuTransparency(alpha)
        config?.setScaleTextSize(textScale)
    }

    actual fun release() {
        released = true
        generation++
        loadJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        view?.setCallback(null)
        view?.release()
        parser?.release()
        view = null
        parser = null
        prepared = false
    }
}
