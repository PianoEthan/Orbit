package com.qx.orbit.bili.vision

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.qx.orbit.bili.core.api.DanmakuGateway
import io.github.ynotbili.dfmnext.danmaku.model.BaseDanmaku
import io.github.ynotbili.dfmnext.ui.widget.DanmakuView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class VisionDanmakuFramePacingTest {
    @get:Rule val compose = createComposeRule()
    private data class Frame(val wallMs: Long, val mediaMs: Long, val left: Float)

    @Test fun scrollingClockAdvancesEvenlyAtDisplayFramesAndStopsWhilePaused() = checkFramePacing(1f)
    @Test fun doubleSpeedUsesTheSameDisplayCadence() = checkFramePacing(2f)
    @Test fun halfSpeedUsesTheSameDisplayCadence() = checkFramePacing(0.5f)

    private fun checkFramePacing(speed: Float) {
        lateinit var host: VisionDanmakuHost
        lateinit var view: DanmakuView
        val frames = mutableListOf<Frame>()
        val vsyncs = mutableListOf<Long>()
        var recording = false
        var marker: BaseDanmaku? = null
        val xml = ("<i>" + (1..12).joinToString("") { index ->
            "<d p=\"0.1,1,25,16777215,0,0,user,$index\">平滑滚动测试 $index</d>"
        } + "</i>").toByteArray()
        var traversals = 0
        val load = ViewTreeObserver.OnPreDrawListener {
            if (recording && traversals++ % 2 == 0) SystemClock.sleep(10)
            true
        }
        val observer = ViewTreeObserver.OnDrawListener {
            if (recording) view.post {
                if (recording) {
                    val item = requireNotNull(marker)
                    frames += Frame(view.drawingTime, requireNotNull(view.getHandler()).timer.currMillisecond, item.getLeft())
                }
            }
        }
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (recording) {
                    vsyncs += frameTimeNanos
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            host = remember { VisionDanmakuHost(DanmakuGateway { xml }) }
            DisposableEffect(host) {
                host.load(101, scope)
                onDispose { host.release() }
            }
            AndroidView(factory = { DanmakuView(it).also { target -> view = target; host.bindView(target) } }, modifier = Modifier.fillMaxSize())
        }
        compose.waitUntil(10000) { compose.runOnIdle { host.isReady } }
        compose.runOnIdle { host.setEnabled(true); host.synchronize(1000, true, speed) }
        compose.waitUntil(10000) { compose.runOnIdle { host.currentTime > 1300 } }
        compose.waitUntil(5000) { compose.runOnIdle { view.getCurrentVisibleDanmakus()?.first() != null } }
        compose.runOnIdle {
            marker = view.getCurrentVisibleDanmakus()?.first()
            requireNotNull(marker).duration?.setValue(15000L)
            view.viewTreeObserver.addOnDrawListener(observer)
            view.viewTreeObserver.addOnPreDrawListener(load)
            recording = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        compose.waitUntil(5000) { compose.runOnIdle { frames.size >= 100 } }
        compose.runOnIdle {
            recording = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            view.viewTreeObserver.removeOnDrawListener(observer)
            view.viewTreeObserver.removeOnPreDrawListener(load)
            host.setPlaying(false)
        }
        val deltas = frames.zipWithNext { a, b -> (b.wallMs - a.wallMs) to (b.mediaMs - a.mediaMs) }
        val repeated = deltas.count { it.first > 0 && it.second <= 0 }
        val uneven = deltas.count { abs(it.second - it.first * speed) > 3 }
        val ratio = frames.size.toDouble() / vsyncs.size
        val elapsed = frames.last().wallMs - frames.first().wallMs
        val fps = (frames.size - 1) * 1000.0 / elapsed
        Log.i("DanmakuFramePacing", "speed=$speed frames=${frames.size} vsyncs=${vsyncs.size} fps=$fps ratio=$ratio repeated=$repeated uneven=$uneven")
        assertTrue("Too many repeated media positions: $repeated", repeated <= deltas.size / 20)
        assertTrue("Media-clock steps do not match display-frame steps: $uneven/${deltas.size}", uneven <= deltas.size / 10)
        assertTrue("Only $ratio of display frames rendered danmaku", ratio >= 0.9)
        val totalMedia = frames.last().mediaMs - frames.first().mediaMs
        val pixelsPerMs = (frames.first().left - frames.last().left) / totalMedia
        assertTrue("Scrolling text did not move left", pixelsPerMs > 0f)
        val badMotion = frames.zipWithNext().count { (a, b) -> abs((a.left - b.left) - (b.mediaMs - a.mediaMs) * pixelsPerMs) > 1f }
        assertTrue("Scrolling position does not match frame clock: $badMotion", badMotion <= frames.size / 10)
        compose.waitUntil(5000) { compose.runOnIdle { view.isPaused() } }
        val paused = compose.runOnIdle { host.currentTime }
        val until = SystemClock.elapsedRealtime() + 250
        compose.waitUntil(2000) { SystemClock.elapsedRealtime() >= until }
        compose.runOnIdle { assertEquals(paused, host.currentTime) }
    }
}
