package com.qx.orbit.bili.vision

import android.view.SurfaceView
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.qx.orbit.bili.core.api.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VisionPlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val video = CoreVideo(42, "BV-player", "播放器测试视频", "", "作者", cid = 101)
    private val qualities = listOf(CorePlaybackQuality(64, "720P"), CorePlaybackQuality(32, "480P"))
    private val pages = listOf(
        CoreVideoPage(1, "第一部分", 101, 120),
        CoreVideoPage(2, "第二部分", 102, 180),
        CoreVideoPage(3, "第三部分", 103, 240),
    )

    private fun testGateway(playback: CorePlayback) = object : PlaybackGateway {
        override suspend fun resolve(video: CoreVideo) = playback
        override suspend fun resolveQuality(video: CoreVideo, quality: Int) = playback.copy(
            quality = quality,
            video = video,
        )
    }

    private fun testEpisodesModel(pagesList: List<CoreVideoPage>) = object : VideoGateway {
        override suspend fun loadDetails(video: CoreVideo) =
            CoreVideoDetails(video, "简介", 540, 1, 2, 3, pagesList)
    }

    @Test fun playerDialogsAllowSelectingQualitySpeedAndEpisode() {
        val playback = CorePlayback(
            video = video,
            urls = listOf("https://cdn.test/video.mp4"),
            quality = 64,
            qualities = qualities,
            audioUrl = "https://cdn.test/audio.mp4",
        )
        val gateway = testGateway(playback)
        val detailsGateway = testEpisodesModel(pages)

        compose.setContent {
            val episodesModel = remember { VisionPlayerEpisodesViewModel(detailsGateway) }
            val subtitleModel = remember { VisionSubtitleViewModel(object : SubtitleGateway {
                override suspend fun loadTracks(video: CoreVideo, cookie: String) = listOf(CoreSubtitleTrack(1, "zh", "中文字幕", "https://aisubtitle.hdslb.com/test", false))
                override suspend fun loadCues(track: CoreSubtitleTrack, cookie: String) = listOf(CoreSubtitleCue(0, 10000, "当前播放字幕"))
            }) }
            VisionTheme {
                VisionPlayerScreen(
                    video = video,
                    onBack = {},
                    gateway = gateway,
                    episodesModel = episodesModel,
                    danmakuGateway = DanmakuGateway { null },
                    subtitleModel = subtitleModel,
                    playerFactory = { _ -> object : AndroidVisionPlayerAdapter {
                        override fun bindSurfaceView(surfaceView: SurfaceView) = Unit
                        override fun prepare(playback: CorePlayback, segment: Int, positionMs: Long) = Unit
                        override fun play() = Unit
                        override fun pause() = Unit
                        override fun seekTo(positionMs: Long) = Unit
                        override fun setPlaybackSpeed(speed: Float) = Unit
                        override fun release() = Unit
                        override fun setListener(listener: VisionPlayerListener?) = Unit
                        override val currentPosition = 0L
                        override val duration = 120000L
                        override val currentMediaItemIndex = 0
                        override val mediaItemCount = 1
                        override val segmentDurationsMs = listOf(120000L)
                        override val isPlaying = false
                    } },
                )
            }
        }

        compose.onNodeWithText(if (VisionPreferences.danmakuEnabled) "弹幕：开" else "弹幕：关").assertIsDisplayed().performClick()
        compose.onNodeWithText(if (VisionPreferences.danmakuEnabled) "弹幕：开" else "弹幕：关").assertIsDisplayed()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, "vision-player.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Verify quality dialog: initial default quality is 480P
        compose.onNodeWithText("480P").assertIsDisplayed().performClick()
        compose.onNodeWithText("选择清晰度").assertIsDisplayed()
        compose.onNodeWithText("720P").performClick()
        compose.onNodeWithText("选择清晰度").assertDoesNotExist()
        compose.onNodeWithText("720P").assertIsDisplayed()

        // Verify speed dialog
        compose.onNodeWithText("1.0×").assertIsDisplayed().performClick()
        compose.onNodeWithText("播放速度").assertIsDisplayed()
        compose.onNodeWithText("1.5×").performClick()
        compose.onNodeWithText("播放速度").assertDoesNotExist()
        compose.onNodeWithText("1.5×").assertIsDisplayed()

        compose.onNodeWithText("CC 字幕").performClick()
        compose.onNodeWithText("中文字幕").performClick()
        compose.onNodeWithText("当前播放字幕").assertIsDisplayed()
        compose.onNodeWithText("放大视频").performClick()
        compose.onNodeWithText("还原视频").performClick()

        // Verify episode picker dialog
        compose.onNodeWithText("选集 P1").assertIsDisplayed().performClick()
        compose.onNodeWithText("选择分 P").assertIsDisplayed()
        compose.onNodeWithText("P2 第二部分").performScrollTo().performClick()
        compose.onNodeWithText("选择分 P").assertDoesNotExist()
        compose.onNodeWithText("选集 P2").assertIsDisplayed()
    }
}
