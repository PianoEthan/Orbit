package com.qx.orbit.bili.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DanmakuPlaybackClockTest(private val engine: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines() = listOf(arrayOf("DFMNext"), arrayOf("DFM"))
    }

    private val next = io.github.ynotbili.dfmnext.danmaku.model.PlaybackClock()
    private val flame = master.flame.danmaku.danmaku.model.PlaybackClock()
    private fun position(now: Long) = if (engine == "DFMNext") next.positionAt(now) else flame.positionAt(now)
    private fun resume(position: Long, now: Long) {
        if (engine == "DFMNext") next.resume(position, now) else flame.resume(position, now)
    }
    private fun pause(now: Long) = if (engine == "DFMNext") next.pause(now) else flame.pause(now)
    private fun speed(value: Float, now: Long) {
        if (engine == "DFMNext") next.setSpeed(value, now) else flame.setSpeed(value, now)
    }
    private fun delay(media: Long) = if (engine == "DFMNext") next.delayFor(media) else flame.delayFor(media)

    @Test
    fun followsSpeedRegardlessOfFrameFrequency() {
        for (rate in listOf(0.5f, 1f, 1.5f, 2f, 3f)) {
            speed(rate, 0)
            resume(10000, 0)
            for (time in listOf(16L, 250L, 1000L, 60000L)) {
                assertEquals(10000L + (time * rate).toLong(), position(time))
            }
        }
    }

    @Test
    fun changingSpeedPreservesPositionIncludingLongPressRelease() {
        resume(10000, 0)
        speed(2f, 1000)
        assertEquals(11000L, position(1000))
        assertEquals(13000L, position(2000))
        speed(3f, 2000)
        assertEquals(16000L, position(3000))
        speed(1f, 3000)
        assertEquals(17000L, position(4000))
    }

    @Test
    fun pauseFreezesTimeAndResumeAndSeekRetainSpeed() {
        speed(2f, 0)
        resume(10000, 0)
        val paused = pause(1000)
        assertEquals(12000L, paused)
        assertEquals(paused, position(9000))
        resume(paused, 9000)
        assertEquals(14000L, position(10000))
        resume(5000, 10000)
        assertEquals(7000L, position(11000))
    }

    @Test
    fun idleWaitUsesMediaSpeedAndSpeedChangesWhilePausedDoNotAdvanceTime() {
        speed(2f, 0)
        assertEquals(1000L, delay(2000))
        resume(10000, 0)
        pause(1000)
        speed(0.5f, 9000)
        assertEquals(12000L, position(10000))
        assertEquals(4000L, delay(2000))
    }

    @Test
    fun fractionalFramesDoNotAccumulateRoundingDrift() {
        speed(1.5f, 0)
        resume(0, 0)
        for (time in 1L..10000L) {
            position(time)
            speed(1.5f, time)
        }
        assertEquals(15000L, position(10000))
    }

    @Test
    fun scrollingDanmakuPositionAndLifetimeFollowTheClock() {
        speed(2f, 0)
        resume(0, 0)
        if (engine == "DFMNext") {
            val item = object : io.github.ynotbili.dfmnext.danmaku.model.R2LDanmaku(
                io.github.ynotbili.dfmnext.danmaku.model.Duration(4000)
            ) { init { mStepX = 0.1f } }
            item.flags = io.github.ynotbili.dfmnext.danmaku.model.GlobalFlagValues()
            val timer = io.github.ynotbili.dfmnext.danmaku.model.DanmakuTimer()
            item.setTimer(timer)
            val display = java.lang.reflect.Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(io.github.ynotbili.dfmnext.danmaku.model.IDisplayer::class.java)
            ) { _, method, _ -> if (method.name == "getWidth") 400 else null }
                as io.github.ynotbili.dfmnext.danmaku.model.IDisplayer
            timer.update(position(1000))
            item.layout(display, 0f, 0f)
            assertEquals(200f, item.getLeft(), 0.01f)
            timer.update(position(2000))
            assertEquals(true, item.isTimeOut())
        } else {
            val item = object : master.flame.danmaku.danmaku.model.R2LDanmaku(
                master.flame.danmaku.danmaku.model.Duration(4000)
            ) { init { mStepX = 0.1f } }
            item.flags = master.flame.danmaku.danmaku.model.GlobalFlagValues()
            val timer = master.flame.danmaku.danmaku.model.DanmakuTimer()
            item.setTimer(timer)
            val display = java.lang.reflect.Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(master.flame.danmaku.danmaku.model.IDisplayer::class.java)
            ) { _, method, _ -> if (method.name == "getWidth") 400 else null }
                as master.flame.danmaku.danmaku.model.IDisplayer
            timer.update(position(1000))
            item.layout(display, 0f, 0f)
            assertEquals(200f, item.left, 0.01f)
            timer.update(position(2000))
            assertEquals(true, item.isTimeOut)
        }
    }

    @Test
    fun invalidRatesAreIgnored() {
        speed(2f, 0)
        resume(0, 0)
        for (rate in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) speed(rate, 500)
        assertEquals(2000L, position(1000))
    }
}
