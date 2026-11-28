package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.HistoryReporter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionPlaybackHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val cookie = "SESSDATA=fake; bili_jct=fake-csrf"
    private fun progress(seconds: Long, cid: Long = 101) = VisionHistoryProgress(42, cid, seconds)

    @Test fun throttlesSamplesAndFlushesExactLatestPositionWithoutDuplicatePauseWrites() = runTest(dispatcher) {
        val sent = mutableListOf<VisionHistoryProgress>()
        val vm = VisionPlaybackHistoryViewModel(HistoryReporter { aid, cid, seconds, _, _ -> sent += VisionHistoryProgress(aid, cid, seconds) }) { testScheduler.currentTime }
        vm.setAccount(cookie)
        vm.sample(progress(0), playing = false, force = true); runCurrent(); assertTrue(sent.isEmpty())
        vm.sample(progress(1), playing = true); runCurrent()
        advanceTimeBy(14000); vm.sample(progress(15), playing = true); runCurrent(); assertEquals(listOf(progress(1)), sent)
        advanceTimeBy(1000); vm.sample(progress(16), playing = true); runCurrent()
        vm.sample(progress(19), playing = false, force = true); vm.flush(); runCurrent()
        assertEquals(listOf(progress(1), progress(16), progress(19)), sent)
    }

    @Test fun serializesWritesAndKeepsLatestPendingPositionForEachEpisode() = runTest(dispatcher) {
        val sent = mutableListOf<VisionHistoryProgress>()
        val gate = CompletableDeferred<Unit>()
        val vm = VisionPlaybackHistoryViewModel(HistoryReporter { aid, cid, seconds, _, _ ->
            sent += VisionHistoryProgress(aid, cid, seconds)
            if (sent.size == 1) gate.await()
        }) { testScheduler.currentTime }
        vm.setAccount(cookie); vm.sample(progress(1), true); runCurrent()
        vm.sample(progress(4), true); vm.flush()
        vm.sample(progress(9), true); vm.flush()
        vm.sample(progress(0, 102), true)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(progress(1), progress(9), progress(0, 102)), sent)
    }

    @Test fun accountChangeDropsQueuedOldAccountAndIgnoresItsLateFailure() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val cookies = mutableListOf<String>()
        val vm = VisionPlaybackHistoryViewModel(HistoryReporter { _, _, _, session, _ ->
            cookies += session
            if (session == cookie) withContext(NonCancellable) { gate.await(); error("offline") }
        }) { testScheduler.currentTime }
        vm.setAccount(cookie); vm.sample(progress(1), true); runCurrent(); vm.sample(progress(2), true); vm.flush()
        val newCookie = "SESSDATA=new; bili_jct=new-csrf"
        vm.setAccount(newCookie); vm.sample(progress(3), true); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(cookie, newCookie), cookies); assertFalse(vm.syncFailed.value)
    }

    @Test fun failedSyncDoesNotLoopAndLaterProgressCanRecoverIncludingTimeout() = runTest(dispatcher) {
        var calls = 0
        val vm = VisionPlaybackHistoryViewModel(HistoryReporter { _, _, _, _, _ ->
            calls++
            if (calls == 1) delay(20000)
        }) { testScheduler.currentTime }
        vm.setAccount(cookie); vm.sample(progress(1), true); advanceUntilIdle()
        assertEquals(1, calls); assertTrue(vm.syncFailed.value)
        advanceTimeBy(15000); vm.sample(progress(2), true); advanceUntilIdle()
        assertEquals(2, calls); assertFalse(vm.syncFailed.value)
        vm.setAccount(""); vm.sample(progress(3), true, true); vm.flush(); advanceUntilIdle(); assertEquals(2, calls)
    }

    @Test fun leavingFlushesBeforeClearingAndUnplayedReturnCannotOverwriteOldPosition() = runTest(dispatcher) {
        val sent = mutableListOf<VisionHistoryProgress>()
        val vm = VisionPlaybackHistoryViewModel(HistoryReporter { aid, cid, seconds, _, _ -> sent += VisionHistoryProgress(aid, cid, seconds) }) { testScheduler.currentTime }
        vm.setAccount(cookie); vm.sample(progress(1), true); runCurrent()
        vm.sample(progress(9), true); vm.endPlayback(); advanceUntilIdle()
        vm.sample(progress(0), false, true); vm.flush(); advanceUntilIdle()
        assertEquals(listOf(progress(1), progress(9)), sent)
    }

    @Test fun segmentProgressAndHistoryResumeUseSameCidTimeline() {
        assertEquals(1 to 5000L, playbackSegmentPosition(25000, listOf(20000, 30000)))
        assertEquals(1 to 0L, playbackSegmentPosition(20000, listOf(20000, 30000)))
        assertEquals(1 to 30000L, playbackSegmentPosition(90000, listOf(20000, 30000)))
        assertEquals(0 to 45000L, playbackSegmentPosition(45000, emptyList()))
        assertEquals(25L, historyPositionSeconds(1, 5000, listOf(20000)))
        assertNull(historyPositionSeconds(1, 5000, listOf(-1)))
        assertNull(historyPositionSeconds(2, 5000, listOf(20000)))
        assertEquals(45000L, resumeHistoryPosition(45, 100))
        assertEquals(0L, resumeHistoryPosition(-1, 100))
        assertEquals(0L, resumeHistoryPosition(100, 100))
    }
}
