package com.qx.orbit.bili.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioHoldSeekLogicTest {
    @Test
    fun offsetStartsFromZeroAndIsNeverNegative() {
        assertEquals(0L, audioHoldSeekOffsetMs(0L))
        assertEquals(0L, audioHoldSeekOffsetMs(-500L))
        assertEquals(0L, audioHoldSeekOffsetMs(Long.MIN_VALUE))
    }

    @Test
    fun offsetGrowsMonotonicallyWhileHolding() {
        var previous = 0L
        var heldMs = 0L
        while (heldMs <= 20_000L) {
            val offset = audioHoldSeekOffsetMs(heldMs)
            assertTrue("offset regressed at $heldMs", offset >= previous)
            previous = offset
            heldMs += 50L
        }
    }

    @Test
    fun shortAccidentalHoldOnlySkipsAFewSeconds() {
        // 误触按住半秒只前进 3 秒，避免长按误判导致大幅跳转
        assertEquals(3_000L, audioHoldSeekOffsetMs(500L))
    }

    @Test
    fun offsetFollowsTheAcceleratingCurve() {
        assertEquals(8_000L, audioHoldSeekOffsetMs(1_000L))
        assertEquals(24_000L, audioHoldSeekOffsetMs(2_000L))
        // 速率在 4.5 秒封顶：4 * 1e3 * t + 8 * 1e3 * t^2 / 2 = 99_000
        assertEquals(99_000L, audioHoldSeekOffsetMs(4_500L))
        // 封顶后按 40 倍速线性前进
        assertEquals(99_000L + 40_000L * 5, audioHoldSeekOffsetMs(9_500L))
    }

    @Test
    fun targetKeepsTheOriginalPositionWhenDurationIsUnknown() {
        assertEquals(12_000L, audioHoldSeekTargetMs(12_000L, 5_000L, 0L))
        assertEquals(0L, audioHoldSeekTargetMs(-1_000L, 5_000L, 0L))
    }

    @Test
    fun targetClampsToTheEndOfTheTrack() {
        assertEquals(60_000L, audioHoldSeekTargetMs(50_000L, 30_000L, 60_000L))
        assertEquals(60_000L, audioHoldSeekTargetMs(60_000L, 1L, 60_000L))
    }

    @Test
    fun targetNeverMovesBackwardsForHugeOffsetsOrStarts() {
        assertEquals(0L, audioHoldSeekTargetMs(0L, 0L, 60_000L))
        assertEquals(60_000L, audioHoldSeekTargetMs(Long.MAX_VALUE, Long.MAX_VALUE, 60_000L))
        assertEquals(60_000L, audioHoldSeekTargetMs(30_000L, Long.MAX_VALUE, 60_000L))
    }

    @Test
    fun rewindDeltaClampsAtTheStartOfTheTrack() {
        assertEquals(7_000L, audioHoldSeekTargetMs(12_000L, -5_000L, 60_000L))
        assertEquals(0L, audioHoldSeekTargetMs(3_000L, -5_000L, 60_000L))
        assertEquals(0L, audioHoldSeekTargetMs(30_000L, Long.MIN_VALUE, 60_000L))
    }
}
