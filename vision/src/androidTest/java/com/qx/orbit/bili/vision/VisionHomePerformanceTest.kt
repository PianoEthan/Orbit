package com.qx.orbit.bili.vision

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.qx.orbit.bili.core.api.CoreFeed
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.FeedGateway
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Collections

class VisionHomePerformanceTest {
    @get:Rule val compose = createComposeRule()
    private fun Context.activity(): Activity = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> error("Activity unavailable")
    }

    @Test fun scrollAndRemoteFocusKeepCardsUsable() {
        val videos = (1..180).map { CoreVideo(it.toLong(), "BV$it", "首页视频 $it · 长标题性能回归", "", "UP 主 $it", durationText = "12:30", viewText = "12.5万") }
        val gateway = object : FeedGateway {
            override suspend fun loadFeed(feed: CoreFeed, page: Int) = videos
            override suspend fun removeVideo(video: CoreVideo) = Unit
        }
        var opened: CoreVideo? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val handlerThread = HandlerThread("VisionFrameMetrics").apply { start() }
        lateinit var window: Window
        val observer = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            durations += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        }
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            window = LocalView.current.context.activity().window
            val model = remember { VisionHomeViewModel(gateway) }
            VisionTheme { VisionVideoFeed(model, { opened = it }, source = "performance") }
        }
        compose.runOnIdle { window.addOnFrameMetricsAvailableListener(observer, Handler(handlerThread.looper)) }
        try {
            val grid = compose.onNode(hasScrollToIndexAction())
            listOf(30, 60, 100, 140, 170, 100, 60, 0).forEach { grid.performScrollToIndex(it) }
            grid.performScrollToIndex(0)
            val first = compose.onNode(hasText(videos.first().title) and hasClickAction())
            first.performSemanticsAction(SemanticsActions.RequestFocus)
            first.assertIsFocused()
            first.performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNode(hasText(videos[1].title) and hasClickAction()).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(videos[1].bvid, opened?.bvid) }
            val samples = synchronized(durations) { durations.sorted() }
            assertTrue("No frame samples", samples.isNotEmpty())
            val median = samples[samples.size / 2] / 1_000_000.0
            val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.lastIndex)] / 1_000_000.0
            Log.i("VisionHomePerformance", "package=${context.packageName} samples=${samples.size} medianMs=$median p95Ms=$p95")
        } finally {
            compose.runOnIdle { window.removeOnFrameMetricsAvailableListener(observer) }
            handlerThread.quitSafely()
        }
    }

    @Test fun thumbnailTransformsOnlyBilibiliImagesAndBoundsPixelSize() {
        assertEquals("https://i0.hdslb.com/bfs/a.jpg@400w_225h_1c.webp", visionThumbnailUrl("https://i0.hdslb.com/bfs/a.jpg", 365))
        assertEquals("https://i0.hdslb.com/a.jpg@1280w_720h_1c.webp", visionThumbnailUrl("https://i0.hdslb.com/a.jpg", 4000))
        listOf("https://other.test/a.jpg", "https://i0.hdslb.com/a.jpg?token=abc", "https://i0.hdslb.com/a.jpg@320w.webp").forEach {
            assertEquals(it, visionThumbnailUrl(it, 640))
        }
    }
}
