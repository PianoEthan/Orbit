package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionPlayerEpisodesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val video = CoreVideo(42, "BV-test", "标题", "", "作者", 101)
    private val pages = listOf(CoreVideoPage(1, "第一集", 101, 60), CoreVideoPage(2, "第二集", 102, 90))
    private fun details(target: CoreVideo, entries: List<CoreVideoPage> = pages) = CoreVideoDetails(target, "", 150, 0, 0, 0, entries)
    private fun gateway(load: suspend (CoreVideo) -> CoreVideoDetails) = object : VideoGateway {
        override suspend fun loadDetails(video: CoreVideo) = load(video)
    }

    @Test fun preservesServerOrderAndDropsDuplicateAndInvalidCids() = runTest(dispatcher) {
        var requests = 0
        val vm = VisionPlayerEpisodesViewModel(gateway { requests++; details(it, pages + pages[0] + pages[0].copy(cid = 0)) })
        vm.open(video); vm.open(video); advanceUntilIdle()
        assertEquals(pages, vm.state.value.pages); assertEquals(1, requests)
        vm.open(video.copy(cid = 102)); advanceUntilIdle(); assertEquals(1, requests)
    }

    @Test fun failedLoadIsRecoverableAndDoesNotEraseExistingEpisodes() = runTest(dispatcher) {
        var fail = true
        val vm = VisionPlayerEpisodesViewModel(gateway { if (fail) error("offline") else details(it) })
        vm.open(video); advanceUntilIdle(); assertNotNull(vm.state.value.error)
        fail = false; vm.retry(); advanceUntilIdle(); assertEquals(pages, vm.state.value.pages)
        fail = true; vm.retry(); advanceUntilIdle()
        assertEquals(pages, vm.state.value.pages); assertNotNull(vm.state.value.error); assertFalse(vm.state.value.loading)
    }

    @Test fun switchingVideosDiscardsLatePreviousDetails() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreVideoDetails>()
        val vm = VisionPlayerEpisodesViewModel(gateway { if (it.bvid == video.bvid) withContext(NonCancellable) { old.await() } else details(it, listOf(pages[1])) })
        vm.open(video); runCurrent()
        vm.open(video.copy(bvid = "BV-new")); runCurrent()
        old.complete(details(video)); advanceUntilIdle()
        assertEquals(listOf(pages[1]), vm.state.value.pages)
    }

    @Test fun qualityChangesKeepPositionButEpisodeChangesResetAllSegments() {
        assertEquals(2 to 45000L, playbackSwitchPosition(101, 101, 2 to 45000L))
        assertEquals(0 to 0L, playbackSwitchPosition(101, 102, 2 to 45000L))
    }

    @Test fun cancelledResolutionCannotReplaceLatestEpisodeEvenIfGatewayIgnoresCancellation() = runTest(dispatcher) {
        val old = CompletableDeferred<CorePlayback>()
        val received = mutableListOf<Long>()
        val gateway = object : PlaybackGateway {
            override suspend fun resolve(video: CoreVideo): CorePlayback =
                if (video.cid == 101L) withContext(NonCancellable) { old.await() } else CorePlayback(video, listOf("https://new"))
        }
        val first = launch { received += resolveVisionPlayback(gateway, video, 32).video.cid }
        runCurrent(); first.cancel()
        received += resolveVisionPlayback(gateway, video.copy(cid = 102), 32).video.cid
        old.complete(CorePlayback(video, listOf("https://old"))); advanceUntilIdle()
        assertEquals(listOf(102L), received)
    }

    @Test fun failedOrMismatchedResolutionNeverProducesAReplacement() = runTest(dispatcher) {
        val gateway = object : PlaybackGateway {
            override suspend fun resolve(video: CoreVideo) = CorePlayback(video.copy(cid = 999), listOf("https://wrong"))
        }
        try { resolveVisionPlayback(gateway, video, 32); fail("Must reject wrong CID") } catch (_: IllegalStateException) { }
        val failing = object : PlaybackGateway {
            override suspend fun resolve(video: CoreVideo): CorePlayback = error("offline")
        }
        try { resolveVisionPlayback(failing, video, 32); fail("Must fail") } catch (error: IllegalStateException) { assertEquals("offline", error.message) }
    }
}
