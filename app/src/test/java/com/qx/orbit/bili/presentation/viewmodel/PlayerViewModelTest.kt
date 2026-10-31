package com.qx.orbit.bili.presentation.viewmodel

import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.util.player.OrbitPlayer
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private lateinit var model: PlayerViewModel
    private var speed = 1f
    private var position = 0L
    private var seekCount = 0
    private var pauseCount = 0
    private var requestedPosition = 0L
    private var delaySeek = false
    private var duration = 120_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val player = Proxy.newProxyInstance(
            OrbitPlayer::class.java.classLoader, arrayOf(OrbitPlayer::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getDuration" -> duration
                "getCurrentPosition" -> position
                "getTcpSpeed" -> 0L
                "setSpeed" -> { speed = args!![0] as Float; null }
                "seekTo" -> {
                    requestedPosition = args!![0] as Long
                    if (!delaySeek) position = requestedPosition
                    seekCount++
                    null
                }
                "pause" -> { pauseCount++; null }
                else -> null
            }
        } as OrbitPlayer
        model = PlayerViewModel()
        PlayerViewModel::class.java.getDeclaredField("_player").apply {
            isAccessible = true
            set(model, player)
        }
        model.setData(PlayerData(type = PlayerData.TYPE_LOCAL))
    }

    @After
    fun tearDown() {
        model.release()
        Dispatchers.resetMain()
    }

    @Test
    fun preparationImmediatelyPausesTheOldSourceEvenIfTheNewLoadFails() {
        model.onPrepared()
        position = 60_000L
        model.setLongPressSpeedUp(true)
        model.beginPlaybackPreparation()
        assertEquals(1, pauseCount)
        assertFalse(model.isPlaying.value)
        assertFalse(model.isPrepared.value)
        assertFalse(model.isLongPressSpeedUp.value)
        assertEquals(60_000L, model.currentProgress.value)
        model.setError("网络请求失败")
        model.setPlaybackAllowed(false)
        assertEquals(1, pauseCount)
        assertFalse(model.isPlaying.value)
    }

    @Test
    fun relativeSeekUsesTheLivePositionAndAccumulatesBeforeEngineSeekCompletes() {
        model.onPrepared()
        assertEquals(0L, model.currentProgress.value)
        position = 60_000L
        delaySeek = true
        assertEquals(61_000L, model.seekBy(1_000f))
        assertEquals(62_000L, model.seekBy(1_000f))
        assertEquals(62_000L, requestedPosition)
        assertEquals(60_000L, position)
        model.seekTo(90_000L)
        position = 90_000L
        assertEquals(91_000L, model.seekBy(1_000f))
    }

    @Test
    fun rapidBufferingPairStillChangesTheSynchronizationRevision() {
        model.onPrepared()
        val initialLoading = model.isLoading.value
        val initialRevision = model.bufferingRevision.value
        model.onInfo(OrbitPlayer.MEDIA_INFO_BUFFERING_START, 0)
        model.onInfo(OrbitPlayer.MEDIA_INFO_BUFFERING_END, 0)
        assertEquals(initialLoading, model.isLoading.value)
        assertEquals(initialRevision + 2L, model.bufferingRevision.value)
        assertTrue(model.isPlaying.value)
    }

    @Test
    fun speedChangesWhileHoldingKeepTemporarySpeedAndRestoreLatestPreference() {
        model.onPrepared()
        model.setSpeed(1.5f)
        model.setLongPressSpeedUp(true)
        assertEquals(2f, speed)
        model.setSpeed(0.5f)
        assertEquals(2f, speed)
        assertEquals(2f, model.effectiveSpeed.value)
        model.setLongPressSpeedUp(false)
        assertEquals(0.5f, speed)
        assertFalse(model.isLongPressSpeedUp.value)
    }

    @Test
    fun pauseCompletionErrorAndPreparationClearTemporarySpeed() {
        val stopActions: List<() -> Unit> = listOf(
            { model.pause() }, { model.onCompletion() },
            { model.onError(1, 0) }, { model.beginPlaybackPreparation() }
        )
        stopActions.forEach { stop ->
            model.onPrepared()
            model.setSpeed(1.5f)
            model.setLongPressSpeedUp(true)
            stop()
            assertFalse(model.isLongPressSpeedUp.value)
            assertFalse(model.isPlaying.value)
            assertEquals(1.5f, speed)
        }
    }

    @Test
    fun localCompletionStopsPlaybackEvenWithoutHeartbeatReporting() {
        model.onPrepared()
        assertTrue(model.isPlaying.value)
        model.onCompletion()
        assertFalse(model.isPlaying.value)
        assertFalse(model.isLoading.value)
        assertEquals(duration, model.currentProgress.value)
    }

    @Test
    fun bufferingBackgroundAndReleaseClearTemporarySpeed() {
        model.onPrepared()
        model.setSpeed(1.5f)
        model.setLongPressSpeedUp(true)
        model.onInfo(OrbitPlayer.MEDIA_INFO_BUFFERING_START, 0)
        assertFalse(model.isLongPressSpeedUp.value)
        model.onInfo(OrbitPlayer.MEDIA_INFO_BUFFERING_END, 0)
        model.setLongPressSpeedUp(true)
        model.setPlaybackAllowed(false)
        assertFalse(model.isLongPressSpeedUp.value)
        model.setPlaybackAllowed(true)
        model.setLongPressSpeedUp(true)
        model.release()
        assertEquals(1.5f, speed)
        assertFalse(model.isLongPressSpeedUp.value)
    }

    @Test
    fun preparationDoesNotAutoplayWhenBackgroundPlaybackIsDisallowed() {
        model.setPlaybackAllowed(false)
        model.onPrepared()
        model.play()
        assertFalse(model.isPlaying.value)
        model.setPlaybackAllowed(true)
        model.play()
        assertTrue(model.isPlaying.value)
    }

    @Test
    fun seekIsForbiddenBeforePreparationForLiveAndForUnknownDuration() {
        model.seekTo(10_000L)
        assertEquals(0, seekCount)
        model.onPrepared()
        model.seekTo(200_000L)
        assertEquals(duration, position)
        model.seekTo(-1L)
        assertEquals(0L, position)
        model.setData(PlayerData(type = PlayerData.TYPE_LIVE))
        model.seekTo(10_000L)
        assertEquals(2, seekCount)
        model.setData(PlayerData(type = PlayerData.TYPE_LOCAL))
        duration = 0L
        model.onPrepared()
        model.seekTo(10_000L)
        assertEquals(2, seekCount)
    }
}
