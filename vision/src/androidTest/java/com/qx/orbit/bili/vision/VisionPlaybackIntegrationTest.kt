package com.qx.orbit.bili.vision

import android.graphics.Color
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.DanmakuGateway
import com.qx.orbit.bili.core.api.PlaybackGateway
import com.qx.orbit.bili.core.api.VideoGateway
import com.qx.orbit.bili.core.api.CorePlaybackQuality
import com.qx.orbit.bili.core.api.CoreVideoDetails
import io.github.ynotbili.dfmnext.ui.widget.DanmakuView
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VisionPlaybackIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val video = CoreVideo(1, "BVtest", "1080p 测试", "", "", cid = 101)

    private fun create1080pFixture(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "vision-1080p-test.mp4")
        val format = MediaFormat.createVideoFormat("video/avc", 1920, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        var writtenFrames = 0L
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()
            val info = MediaCodec.BufferInfo()
            fun drain(end: Boolean) {
                val deadline = System.currentTimeMillis() + 15000
                while (System.currentTimeMillis() < deadline) {
                    val index = encoder.dequeueOutputBuffer(info, if (end) 10000 else 0)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    } else if (index >= 0) {
                        val buffer = requireNotNull(encoder.getOutputBuffer(index))
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            info.presentationTimeUs = writtenFrames++ * 33333
                            muxer.writeSampleData(track, buffer, info)
                        }
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(index, false)
                        if (ended) return
                    } else if (!end) return
                }
                check(!end) { "Encoder EOS timeout" }
            }
            repeat(120) { frame ->
                val canvas = surface.lockHardwareCanvas()
                canvas.drawColor(if (frame % 2 == 0) Color.BLUE else Color.GREEN)
                surface.unlockCanvasAndPost(canvas)
                drain(false)
            }
            encoder.signalEndOfInputStream()
            drain(true)
            surface.release()
        } finally {
            encoder.stop()
            encoder.release()
            if (started) muxer.stop()
            muxer.release()
        }
        return file
    }

    private fun checkPlayback(engine: VisionPlayerEngine, failover: Boolean = false, dash: Boolean = false) {
        val fixture = create1080pFixture()
        val audio = if (dash) File(fixture.parentFile, "vision-test-audio.wav").also { file ->
            val samples = 44100 * 4
            val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(samples * 2)
            file.writeBytes(buffer.array())
        } else null
        lateinit var adapter: VisionPlayerAdapter
        var error: String? = null
        var aspect = 0f
        try {
            compose.setContent {
                val context = LocalContext.current
                adapter = remember { createVisionPlayerAdapter(context, engine) }
                DisposableEffect(adapter) {
                    adapter.setListener(object : VisionPlayerListener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) = Unit
                        override fun onPlaybackStateChanged(isBuffering: Boolean, isEnded: Boolean) = Unit
                        override fun onVideoSizeChanged(aspectRatio: Float) { aspect = aspectRatio }
                        override fun onError(message: String) { error = message }
                    })
                    val stream = if (failover) CorePlayback(video, listOf(fixture.absolutePath + ".missing"), videoBackups = listOf(fixture.absolutePath))
                        else CorePlayback(video, listOf(fixture.absolutePath), audioUrl = audio?.absolutePath)
                    adapter.prepare(stream, 0, 0)
                    adapter.pause()
                    onDispose { adapter.release() }
                }
                AndroidView(factory = { SurfaceView(it).apply { adapter.bindSurfaceView(this) } }, modifier = Modifier.fillMaxSize())
            }
            compose.waitUntil(15000) { compose.runOnIdle { error != null || adapter.duration > 0 } }
            compose.runOnIdle {
                assertNull(error)
                assertFalse("Preparing while paused must not auto-play", adapter.isPlaying)
                adapter.play()
            }
            compose.waitUntil(10000) { compose.runOnIdle { error != null || adapter.currentPosition > 0 } }
            compose.runOnIdle {
                assertNull(error)
                assertEquals(16f / 9f, aspect, 0.01f)
                adapter.pause()
                assertFalse(adapter.isPlaying)
                adapter.setPlaybackSpeed(1.5f)
                adapter.seekTo(0)
                adapter.play()
            }
            compose.waitUntil(10000) { compose.runOnIdle { error != null || adapter.isPlaying } }
            compose.runOnIdle { assertNull(error); adapter.pause() }
            if (dash) {
                compose.runOnIdle { adapter.prepare(CorePlayback(video, listOf(fixture.absolutePath)), 0, 0); adapter.play() }
                compose.waitUntil(15000) { compose.runOnIdle { error != null || adapter.currentPosition > 0 } }
                compose.runOnIdle { assertNull(error); adapter.pause() }
            }
        } finally { fixture.delete(); audio?.delete() }
    }

    @Test fun ijkDecodes1080pAndHonorsPausedPreparation() = checkPlayback(VisionPlayerEngine.IJK)
    @Test fun media3Decodes1080pAndHonorsPausedPreparation() = checkPlayback(VisionPlayerEngine.MEDIA3)
    @Test fun ijkPreferencePlaysDash1080pWithSeparateAudioAndCanReturnToMp4() = checkPlayback(VisionPlayerEngine.IJK, dash = true)

    @Test fun ijkFallsBackToBackupAddress() = checkPlayback(VisionPlayerEngine.IJK, true)
    @Test fun media3FallsBackToBackupAddress() = checkPlayback(VisionPlayerEngine.MEDIA3, true)

    @Test fun fullPlayerDisplays1080pWithDanmakuAndControls() {
        val fixture = create1080pFixture()
        lateinit var adapter: VisionPlayerAdapter
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val xml = "<i><d p=\"0.1,1,25,16777215,0,0,user,1\">Orbit Vision 弹幕测试</d></i>".toByteArray()
        val gateway = object : PlaybackGateway {
            override suspend fun resolve(video: CoreVideo) = CorePlayback(video, listOf(fixture.absolutePath))
            override suspend fun resolveStream(video: CoreVideo, quality: Int, preferProgressive: Boolean): CorePlayback {
                assertTrue(preferProgressive)
                return resolve(video).copy(quality = 80, qualities = listOf(CorePlaybackQuality(80, "1080P")))
            }
        }
        val details = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo) = CoreVideoDetails(video, "", 4, 0, 0, 0)
        }
        compose.runOnIdle {
            VisionPreferences.setPlayerEngine(context, VisionPlayerEngine.IJK)
            VisionPreferences.setDanmakuEnabled(context, true)
            VisionPreferences.setControlsTimeout(context, 0)
        }
        try {
            compose.setContent {
                val episodes = remember { VisionPlayerEpisodesViewModel(details) }
                VisionTheme {
                    VisionPlayerScreen(video, onBack = {}, gateway = gateway, episodesModel = episodes,
                        danmakuGateway = DanmakuGateway { xml },
                        playerFactory = { ctx, engine -> createVisionPlayerAdapter(ctx, engine).also { adapter = it } })
                }
            }
            compose.waitUntil(15000) { compose.runOnIdle { adapter.currentPosition > 500 } }
            compose.onNodeWithText("暂停").performClick()
            compose.onNodeWithText("播放").assertIsDisplayed()
            compose.onNodeWithText("1080P").assertIsDisplayed()
            compose.onNodeWithText("弹幕：开").assertIsDisplayed()
            compose.runOnIdle { assertFalse(adapter.isPlaying) }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.externalCacheDir, "vision-full-player.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            compose.onNodeWithText("播放").performClick()
            compose.waitUntil(10000) { compose.runOnIdle { adapter.isPlaying } }
        } finally { fixture.delete() }
    }

    @Test fun dfmNextParsesRendersSeeksAndPauses() {
        lateinit var host: VisionDanmakuHost
        lateinit var view: DanmakuView
        lateinit var hostScope: CoroutineScope
        val xml = "<i><d p=\"1,1,25,16777215,0,0,user,1\">Orbit Vision 弹幕测试</d></i>".toByteArray()
        compose.setContent {
            val scope = rememberCoroutineScope()
            hostScope = scope
            host = remember { VisionDanmakuHost(DanmakuGateway { xml }) }
            DisposableEffect(host) {
                host.load(101, scope)
                onDispose { host.release() }
            }
            AndroidView(factory = { DanmakuView(it).also { view = it; host.bindView(it) } }, modifier = Modifier.fillMaxSize())
        }
        compose.waitUntil(10000) { compose.runOnIdle { host.isReady } }
        compose.runOnIdle { host.setEnabled(true); host.synchronize(1000, true, 1f) }
        compose.waitUntil(10000) { compose.runOnIdle { host.currentTime > 1100 && (view.getCurrentVisibleDanmakus()?.size() ?: 0) > 0 } }
        compose.runOnIdle { host.synchronize(5000, false, 1.5f) }
        compose.waitUntil(10000) { compose.runOnIdle { kotlin.math.abs(host.currentTime - 5000) < 500 } }
        compose.runOnIdle { host.setEnabled(false) }
        compose.waitUntil(10000) { compose.runOnIdle { view.isPaused() } }
        compose.runOnIdle { host.setEnabled(true); host.load(102, hostScope) }
        compose.waitUntil(10000) { compose.runOnIdle { host.isReady } }
    }
}
