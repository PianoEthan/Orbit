package com.qx.orbit.bili.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPlaybackPolicyTest {
    @Test fun completionStopsAtLastPartAndLoopTakesPriority() {
        assertEquals(PlaybackCompletion.REPEAT, playbackCompletion(true, true, 101, listOf(101, 102)))
        assertEquals(PlaybackCompletion.NEXT, playbackCompletion(false, true, 101, listOf(101, 102)))
        assertEquals(PlaybackCompletion.STOP, playbackCompletion(false, true, 102, listOf(101, 102)))
        assertEquals(PlaybackCompletion.STOP, playbackCompletion(false, true, 999, listOf(101, 102)))
        assertEquals(PlaybackCompletion.STOP, playbackCompletion(false, false, 101, listOf(101, 102)))
    }
    @Test fun speedOptionsMatchSupportedPlaybackRangeAndRejectInvalidRestoredValues() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), visionPlaybackSpeeds)
        visionPlaybackSpeeds.forEach { assertEquals(it, supportedPlaybackSpeed(it)) }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 99f).forEach {
            assertEquals(1f, supportedPlaybackSpeed(it), 0f)
        }
    }

    @Test
    fun fitPreservesVideoRatioInPortraitAndWideWindows() {
        assertEquals(VideoViewport(360f, 202.5f), fitVideoViewport(360f, 800f, 16f / 9f))
        assertEquals(VideoViewport(1920f, 1080f), fitVideoViewport(2560f, 1080f, 16f / 9f))
        assertEquals(VideoViewport(607.5f, 1080f), fitVideoViewport(1920f, 1080f, 9f / 16f))
        assertEquals(VideoViewport(0f, 0f), fitVideoViewport(0f, 1080f, 1f))
        assertEquals(VideoViewport(360f, 202.5f), fitVideoViewport(360f, 800f, Float.NaN))
    }

    @Test
    fun hiddenControlsUseArrowsToSeekAndConfirmToRevealWithoutToggling() {
        assertEquals(PlaybackKeyAction.SEEK_BACKWARD, playbackKeyAction(PlaybackKey.LEFT, false))
        assertEquals(PlaybackKeyAction.SEEK_FORWARD, playbackKeyAction(PlaybackKey.RIGHT, false))
        assertEquals(PlaybackKeyAction.SHOW_CONTROLS, playbackKeyAction(PlaybackKey.CONFIRM, false))
        assertEquals(PlaybackKeyAction.SHOW_CONTROLS, playbackKeyAction(PlaybackKey.DOWN, false))
        assertEquals(PlaybackKeyAction.SHOW_CONTROLS, playbackKeyAction(PlaybackKey.UP, false))
    }

    @Test
    fun visibleControlsKeepNativeFocusAndSliderKeyHandling() {
        listOf(PlaybackKey.LEFT, PlaybackKey.RIGHT, PlaybackKey.UP, PlaybackKey.DOWN, PlaybackKey.CONFIRM).forEach {
            assertEquals(PlaybackKeyAction.PASS, playbackKeyAction(it, true))
        }
        assertEquals(PlaybackKeyAction.TOGGLE, playbackKeyAction(PlaybackKey.PLAY_PAUSE, true))
        assertEquals(PlaybackKeyAction.SEEK_FORWARD, playbackKeyAction(PlaybackKey.FAST_FORWARD, true))
        assertEquals(PlaybackKeyAction.SEEK_BACKWARD, playbackKeyAction(PlaybackKey.REWIND, false))
        assertEquals(PlaybackKeyAction.PLAY, playbackKeyAction(PlaybackKey.PLAY, false))
        assertEquals(PlaybackKeyAction.PAUSE, playbackKeyAction(PlaybackKey.PAUSE, true))
    }

    @Test
    fun lifecycleAndSeekRespectPlaybackBounds() {
        assertTrue(playbackShouldRun(true, true, true))
        assertFalse(playbackShouldRun(true, false, true))
        assertFalse(playbackShouldRun(false, true, true))
        assertFalse(playbackShouldRun(true, true, false))
        assertEquals(0L, seekPlaybackPosition(3000, -10000, 20000))
        assertEquals(20000L, seekPlaybackPosition(15000, 10000, 20000))
    }
}
