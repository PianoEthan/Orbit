package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CorePlaybackQuality
import com.qx.orbit.bili.core.api.CoreLiveRoom
import com.qx.orbit.bili.core.api.CoreLiveStream
import com.qx.orbit.bili.core.api.LiveGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionLiveViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeLive(
        var room: CoreLiveRoom = CoreLiveRoom(1, liveStatus = 1),
        val streams: ArrayDeque<CoreLiveStream> = ArrayDeque(),
        val requests: MutableList<Int> = mutableListOf(),
    ) : LiveGateway {
        override suspend fun loadRoomInfo(roomId: Long) = room
        override suspend fun loadStreamInfo(roomId: Long, qn: Int, cookie: String): CoreLiveStream {
            requests += qn
            return streams.removeFirst()
        }
        override suspend fun loadRecommend(page: Int) = emptyList<CoreLiveRoom>()
        override suspend fun loadFollowed(page: Int, cookie: String) = emptyList<CoreLiveRoom>()
    }

    private val qualities = listOf(CorePlaybackQuality(10000, "原画"), CorePlaybackQuality(250, "超清"))

    @Test fun liveRoomProducesPlayableCorePlayback() = runTest(dispatcher) {
        val gateway = FakeLive(streams = ArrayDeque(listOf(
            CoreLiveStream(liveStatus = 1, qualities = qualities, urlsByQuality = mapOf(10000 to "https://live/a.flv", 250 to "https://live/a250.flv")),
        )))
        val model = VisionLiveViewModel(gateway)
        model.open(23058, "cookie")
        advanceUntilIdle()
        val state = model.state.value
        assertEquals(VisionLiveStatus.LIVE, state.status)
        assertEquals(10000, state.quality)
        assertEquals(listOf("https://live/a.flv"), state.playback?.urls)
        assertEquals(23058L, state.playback?.video?.cid)
        assertEquals("live-23058", state.playback?.video?.bvid)
    }

    @Test fun notStartedAndEndedRoomsSkipStreamRequest() = runTest(dispatcher) {
        val gateway = FakeLive(room = CoreLiveRoom(1, liveStatus = 0), streams = ArrayDeque())
        val model = VisionLiveViewModel(gateway)
        model.open(1, "cookie")
        advanceUntilIdle()
        assertEquals(VisionLiveStatus.NOT_STARTED, model.state.value.status)
        assertNull(model.state.value.playback)
        assertTrue(gateway.requests.isEmpty())

        val ended = FakeLive(room = CoreLiveRoom(1, liveStatus = 2), streams = ArrayDeque())
        val endedModel = VisionLiveViewModel(ended)
        endedModel.open(1, "cookie")
        advanceUntilIdle()
        assertEquals(VisionLiveStatus.ENDED, endedModel.state.value.status)
    }

    @Test fun requestedQualityFallsBackToNearestLowerAccepted() = runTest(dispatcher) {
        val gateway = FakeLive(streams = ArrayDeque(listOf(
            CoreLiveStream(liveStatus = 1, qualities = listOf(CorePlaybackQuality(250, "超清")), urlsByQuality = mapOf(250 to "https://live/low.flv")),
        )))
        val model = VisionLiveViewModel(gateway)
        model.open(1, "cookie", preferredQuality = 10000)
        advanceUntilIdle()
        assertEquals(VisionLiveStatus.LIVE, model.state.value.status)
        assertEquals(250, model.state.value.quality)
        assertEquals("https://live/low.flv", model.state.value.playback?.urls?.single())
    }

    @Test fun selectQualityReloadsStreamAndRetryReusesPreference() = runTest(dispatcher) {
        val gateway = FakeLive(streams = ArrayDeque(listOf(
            CoreLiveStream(liveStatus = 1, qualities = qualities, urlsByQuality = mapOf(10000 to "https://live/a.flv", 250 to "https://live/a250.flv")),
            CoreLiveStream(liveStatus = 1, qualities = qualities, urlsByQuality = mapOf(10000 to "https://live/a.flv", 250 to "https://live/a250.flv")),
        )))
        val model = VisionLiveViewModel(gateway)
        model.open(1, "cookie")
        advanceUntilIdle()
        model.selectQuality(250)
        advanceUntilIdle()
        assertEquals(listOf(10000, 250), gateway.requests)
        assertEquals("https://live/a250.flv", model.state.value.playback?.urls?.single())
    }

    @Test fun missingStreamUrlSurfacesRetryableError() = runTest(dispatcher) {
        val gateway = FakeLive(streams = ArrayDeque(listOf(
            CoreLiveStream(liveStatus = 1, encrypted = true, pwdVerified = false),
        )))
        val model = VisionLiveViewModel(gateway)
        model.open(1, "cookie")
        advanceUntilIdle()
        assertEquals(VisionLiveStatus.ERROR, model.state.value.status)
        assertTrue(model.state.value.error?.contains("密码") == true)
    }
}
