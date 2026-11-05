package com.qx.orbit.bili.presentation.viewmodel

import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.PlayerMetadata
import com.qx.orbit.bili.data.model.PlayerInteraction
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.model.InteractionEdge
import com.qx.orbit.bili.data.model.InteractionQuestion
import com.qx.orbit.bili.data.model.InteractionChoice
import com.qx.orbit.bili.data.model.InteractionStoryNode
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import com.qx.orbit.bili.util.player.OrbitPlayer
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
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
    private val progressReport = CompletableDeferred<Pair<Long, Long>>()

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
        model = PlayerViewModel(reportPlayback = { _, _, _, _ -> })
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

    private fun interactionModel(question: InteractionQuestion, clock: () -> Long = { 0L }) {
        val player = model.player
        model.release()
        model = PlayerViewModel(
            metadata = { _, _ -> InteractionVideoResult.Success(PlayerMetadata(interaction = PlayerInteraction(1))) },
            edge = { _, _, edge -> InteractionVideoResult.Success(InteractionVideo(
                edgeId = edge ?: 1, storyList = listOf(InteractionStoryNode(edgeId = edge ?: 1, cid = 20, isCurrent = 1)),
                edges = InteractionEdge(questions = listOf(question))
            )) }, monotonicMs = clock,
            reportPlayback = { data, position, _, _ -> progressReport.complete(data.cid to position) }
        )
        PlayerViewModel::class.java.getDeclaredField("_player").apply { isAccessible = true; set(model, player) }
        model.open(PlayerData(aid = 9, cid = 10, cids = listOf(10, 11), currentPageIndex = 0, interactionHint = true))
    }

    @Test fun interactionCommitsBeforePlaybackAndCurrentCidMatchesReportingData() = runTest {
        interactionModel(InteractionQuestion(type = 1, choices = listOf(InteractionChoice(id = 2, cid = 20))))
        runCurrent()
        val request = model.interaction.request.value!!
        assertEquals(20L, model.playerData.value.cid)
        assertFalse(model.isPrepared.value)
        assertFalse(model.isPlaying.value)
        assertTrue(model.prepareInteractionMedia(request))
        model.onPrepared()
        assertEquals(1L, model.interaction.state.value.session.visitId)
        assertTrue(model.isPlaying.value)
        assertEquals(0L, position)
        model.onCompletion()
        assertEquals(20L to 120L, progressReport.await())
        assertEquals(0, model.playerData.value.currentPageIndex)
        model.switchToNextEpisode()
        model.switchToPreviousEpisode()
        model.switchToEpisode(1)
        assertEquals(20L, model.playerData.value.cid)
        assertEquals(0, model.playerData.value.currentPageIndex)
        model.play()
        assertFalse(model.isPlaying.value)
        model.seekTo(100)
        assertEquals(0L, position)
        model.setLongPressSpeedUp(true)
        assertFalse(model.isLongPressSpeedUp.value)
        model.release()
    }

    @Test fun backgroundTimedQuestionResumesAfterForegroundPlaybackGateAndReachesTarget() = runTest {
        var now = 0L
        interactionModel(InteractionQuestion(type = 4, startTime = 0, duration = 10_000,
            choices = listOf(InteractionChoice(id = 2, cid = 20, isDefault = 1)))) { now }
        runCurrent()
        val root = model.interaction.request.value!!
        model.prepareInteractionMedia(root)
        model.onPrepared()
        model.observeInteraction()
        now = 500
        model.observeInteraction()
        assertEquals(9500L, model.interaction.state.value.session.question?.remainingMs)
        model.setForeground(false)
        model.setPlaybackAllowed(false)
        now = 50_000
        model.observeInteraction()
        assertEquals(9500L, model.interaction.state.value.session.question?.remainingMs)
        assertFalse(model.isPlaying.value)
        model.setForeground(true)
        assertFalse(model.isPlaying.value)
        model.setPlaybackAllowed(true)
        assertTrue(model.isPlaying.value)
        now = 51_000
        model.observeInteraction()
        assertEquals(8500L, model.interaction.state.value.session.question?.remainingMs)
        now = 59_500
        model.observeInteraction()
        assertEquals(2L, model.interaction.state.value.session.transition?.edgeId)
        runCurrent()
        assertTrue(root.token != model.interaction.request.value?.token)
        assertEquals(2L, model.interaction.state.value.session.transition?.edgeId)
        model.release()
    }

    @Test fun backgroundStoryPauseKeepsMediaPausedAndUntimedQuestionSelectable() = runTest {
        var now = 0L
        interactionModel(InteractionQuestion(type = 4, startTime = 0, duration = 10_000, pauseVideo = 1,
            choices = listOf(InteractionChoice(id = 2, cid = 20, isDefault = 1)))) { now }
        runCurrent()
        model.prepareInteractionMedia(model.interaction.request.value!!)
        model.onPrepared()
        model.observeInteraction()
        assertFalse(model.isPlaying.value)
        assertEquals(null, model.interaction.state.value.session.question?.remainingMs)
        model.setForeground(false)
        model.setPlaybackAllowed(false)
        now = 50_000
        model.setForeground(true)
        model.setPlaybackAllowed(true)
        now = 200_000
        model.observeInteraction()
        val state = model.interaction.state.value.session
        assertFalse(model.isPlaying.value)
        assertTrue(state.question?.pauseRequested == true)
        assertEquals(null, state.question?.remainingMs)
        assertEquals(null, state.transition)
        model.chooseInteraction(0, 0, state.visitId, state.generation)
        assertEquals(2L, model.interaction.state.value.session.transition?.edgeId)
        model.release()
    }

    @Test fun explicitUserPauseIsNotResumedByForegroundOrPlaybackGate() = runTest {
        var now = 0L
        interactionModel(InteractionQuestion(type = 4, startTime = 0, duration = 10_000,
            choices = listOf(InteractionChoice(id = 2, cid = 20, isDefault = 1)))) { now }
        runCurrent()
        model.prepareInteractionMedia(model.interaction.request.value!!)
        model.onPrepared()
        model.observeInteraction()
        model.pause()
        model.setForeground(false)
        model.setPlaybackAllowed(false)
        now = 50_000
        model.setForeground(true)
        model.setPlaybackAllowed(true)
        now = 200_000
        model.observeInteraction()
        assertFalse(model.isPlaying.value)
        assertEquals(10_000L, model.interaction.state.value.session.question?.remainingMs)
        assertEquals(null, model.interaction.state.value.session.transition)
        model.release()
    }

    @Test fun ordinaryBackgroundAudioKeepsPlayingAcrossForegroundChanges() {
        model.setData(PlayerData(aid = 1, cid = 1, audioUrl = "audio"))
        model.onPrepared()
        val pauses = pauseCount
        model.setForeground(false)
        model.setPlaybackAllowed(true)
        assertTrue(model.isPlaying.value)
        assertEquals(pauses, pauseCount)
        model.setForeground(true)
        model.setPlaybackAllowed(true)
        assertTrue(model.isPlaying.value)
        assertEquals(pauses, pauseCount)
    }

    @Test fun ordinaryMultiPartCompletionStillSelectsNextEpisode() {
        model.setData(PlayerData(type = PlayerData.TYPE_LOCAL, cid = 1, cids = listOf(1, 2), localUrls = listOf("a", "b")))
        model.onPrepared()
        model.onCompletion()
        assertEquals(2L, model.playerData.value.cid)
        assertEquals(1, model.playerData.value.currentPageIndex)
        assertEquals("b", model.playerData.value.videoUrl)
    }
}
