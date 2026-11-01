package com.qx.orbit.bili.util.interaction

import com.qx.orbit.bili.data.model.InteractionQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionTimelineTest {
    @Test
    fun automaticAndUntimedQuestionsRequireEndEvenForZeroLengthMedia() {
        for (duration in listOf(-1L, 0L)) {
            for (type in 0..2) {
                val window = window(InteractionQuestion(type = type, duration = duration), 0)
                assertFalse(window.contains(0, false))
                assertTrue(window.contains(0, true))
                assertEquals(type == 0, window.automatic)
                assertNull(window.countdownMs)
                assertFalse(window.pauseRequested)
            }
        }
    }

    @Test
    fun timedOneAndTwoUseDurationNotReverseStartAndNeverBlindlyPause() {
        for (type in 1..2) {
            val window = window(InteractionQuestion(type = type, duration = 3_000, startTimeR = 9_000, pauseVideo = 1))
            assertEquals(7_000, window.startMs)
            assertFalse(window.contains(6_999, false))
            assertTrue(window.contains(7_000, false))
            assertTrue(window.contains(10_000, true))
            assertEquals(3_000L, window.countdownMs)
            assertFalse(window.pauseRequested)
        }
        assertEquals(0, window(InteractionQuestion(type = 1, duration = 20_000)).startMs)
    }

    @Test
    fun typeFourUsesExplicitStartFirstAndHalfOpenWindow() {
        val window = window(InteractionQuestion(type = 4, startTime = 2_000, startTimeR = 5_000, duration = 3_000))
        assertEquals(2_000, window.startMs)
        assertEquals(5_000L, window.endMs)
        assertFalse(window.contains(1_999, false))
        assertTrue(window.contains(2_000, false))
        assertTrue(window.contains(4_999, false))
        assertFalse(window.contains(5_000, false))
        assertEquals(3_000L, window.countdownMs)
        val relative = window(InteractionQuestion(type = 4, startTimeR = 4_000, duration = 1_000))
        assertEquals(6_000, relative.startMs)
    }

    @Test
    fun typeFourPauseIsExplicitAndZeroDurationHasNoCountdown() {
        val paused = window(InteractionQuestion(type = 4, startTime = 0, duration = 1_000, pauseVideo = 1))
        assertTrue(paused.pauseRequested)
        assertNull(paused.countdownMs)
        val zero = window(InteractionQuestion(type = 4, startTime = 5, duration = 0))
        assertTrue(zero.contains(5, false))
        assertFalse(zero.contains(6, false))
        assertNull(zero.countdownMs)
    }

    @Test
    fun unsupportedTimingIsNotGuessed() {
        val invalid = listOf(
            InteractionQuestion(type = 3), InteractionQuestion(type = 127),
            InteractionQuestion(type = 0, duration = 1), InteractionQuestion(type = 4),
            InteractionQuestion(type = 4, startTime = -1, duration = 0),
            InteractionQuestion(type = 4, startTime = 0, duration = -1),
            InteractionQuestion(type = 4, startTimeR = -1, duration = 1),
            InteractionQuestion(type = 4, startTimeR = 10_001, duration = 1),
            InteractionQuestion(type = 4, startTime = Long.MAX_VALUE, duration = 1),
            InteractionQuestion(type = 4, startTime = 0, duration = 1, pauseVideo = 2)
        )
        for (question in invalid) assertTrue(InteractionTimeline.window(question, 10_000) is InteractionTimeline.Unsupported)
        assertTrue(InteractionTimeline.window(InteractionQuestion(type = 1), -1) is InteractionTimeline.Unsupported)
    }

    private fun window(question: InteractionQuestion, durationMs: Long = 10_000): InteractionTimeline.Window =
        InteractionTimeline.window(question, durationMs) as InteractionTimeline.Window
}
