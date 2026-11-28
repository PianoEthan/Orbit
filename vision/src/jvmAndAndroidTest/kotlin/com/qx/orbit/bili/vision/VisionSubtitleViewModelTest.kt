package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionSubtitleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val track = CoreSubtitleTrack(1, "zh", "中文", "https://aisubtitle.hdslb.com/test", false)
    private val video = CoreVideo(1, "BV1", "", "", "", cid = 101)

    @Test fun switchingPartDiscardsLateOldCuesAndTurningOffClearsText() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<CoreSubtitleCue>>()
        val gateway = object : SubtitleGateway {
            override suspend fun loadTracks(video: CoreVideo, cookie: String) = listOf(track)
            override suspend fun loadCues(track: CoreSubtitleTrack, cookie: String) = withContext(NonCancellable) { pending.await() }
        }
        val model = VisionSubtitleViewModel(gateway)
        model.open(video, ""); advanceUntilIdle()
        model.select(track); runCurrent()
        model.open(video.copy(cid = 102), ""); runCurrent()
        pending.complete(listOf(CoreSubtitleCue(0, 1000, "旧字幕"))); advanceUntilIdle()
        assertTrue(model.state.value.cues.isEmpty())
        assertNull(model.state.value.selected)
        model.select(null)
        assertFalse(model.state.value.loading)
    }

    @Test fun activeCueSupportsOverlapSeekAndExclusiveEnd() {
        val cues = listOf(CoreSubtitleCue(1000, 3000, "第一行"), CoreSubtitleCue(2000, 4000, "第二行"))
        assertEquals("", activeSubtitle(cues, 999))
        assertEquals("第一行\n第二行", activeSubtitle(cues, 2500))
        assertEquals("第二行", activeSubtitle(cues, 3000))
        assertEquals("", activeSubtitle(cues, 4000))
        assertEquals("第一行", activeSubtitle(cues, 1000))
    }
}
