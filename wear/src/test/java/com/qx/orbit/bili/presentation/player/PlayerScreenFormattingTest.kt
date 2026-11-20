package com.qx.orbit.bili.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScreenFormattingTest {

    @Test
    fun formatsZeroSeconds() {
        assertEquals("00:00", formatPlayerTime(0L))
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("01:23", formatPlayerTime(83_000L))
        assertEquals("59:59", formatPlayerTime(3_599_000L))
    }

    @Test
    fun formatsHoursMinutesAndSeconds() {
        assertEquals("01:00:00", formatPlayerTime(3_600_000L))
        assertEquals("02:05:30", formatPlayerTime(7_530_000L))
    }

    @Test
    fun handlesNegativeOrZeroGracefully() {
        assertEquals("00:00", formatPlayerTime(-1000L))
    }
}
