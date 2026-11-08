package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionLoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private fun gateway(generate: suspend () -> CoreQrCode, poll: suspend (String) -> CoreQrPollResult) = object : AuthGateway {
        override suspend fun generateQrCode() = generate()
        override suspend fun pollQrCode(qrcodeKey: String) = poll(qrcodeKey)
        override suspend fun getMyProfile(cookie: String): CoreUserProfile = error("Profile must not block login")
    }

    @Test fun repeatedNetworkErrorsBackOffAndResumeNormalCadenceAfterRecovery() = runTest(dispatcher) {
        val times = mutableListOf<Long>()
        val model = VisionLoginViewModel(gateway({ CoreQrCode("https://qr", "key") }, {
            times += testScheduler.currentTime
            when (times.size) {
                1 -> error("offline")
                2 -> CoreQrPollResult(CoreQrPollStatus.ERROR, "temporary")
                3 -> CoreQrPollResult(CoreQrPollStatus.SCANNED_WAITING_CONFIRM, "确认中")
                else -> CoreQrPollResult(CoreQrPollStatus.EXPIRED, "过期")
            }
        }))
        model.start(); advanceTimeBy(16000); runCurrent()
        assertEquals(listOf(2000L, 6000L, 14000L, 16000L), times)
        assertEquals(CoreQrPollStatus.EXPIRED, model.state.value.status)
        model.stop(); model.start(); advanceTimeBy(30000); runCurrent(); assertEquals(4, times.size)
        model.stop()
    }

    @Test fun malformedSuccessNeverReachesSessionAndRequiresFreshQr() = runTest(dispatcher) {
        var polls = 0
        val model = VisionLoginViewModel(gateway({ CoreQrCode("https://qr", "key") }, {
            polls++; CoreQrPollResult(CoreQrPollStatus.SUCCESS, "成功", "DedeUserID=123", 123)
        }))
        model.start(); advanceTimeBy(2000); runCurrent()
        assertNull(model.state.value.result); assertEquals(CoreQrPollStatus.EXPIRED, model.state.value.status)
        advanceTimeBy(6000); runCurrent(); assertEquals(1, polls)
        model.stop()
    }

    @Test fun stoppedGenerationCannotPublishLateQrOrStayLoadingOnResume() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreQrCode>()
        var generations = 0
        val model = VisionLoginViewModel(gateway({
            generations++
            if (generations == 1) withContext(NonCancellable) { old.await() } else CoreQrCode("https://qr/new", "new")
        }, { CoreQrPollResult(CoreQrPollStatus.EXPIRED, "过期") }))
        model.start(); runCurrent(); model.stop(); model.start(); runCurrent()
        old.complete(CoreQrCode("https://qr/old", "old")); runCurrent()
        assertEquals("new", model.state.value.code?.qrcodeKey); assertFalse(model.state.value.loading)
        model.stop()
    }

    @Test fun refreshDiscardsLateOldPollAndPollsOnlyNewKey() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreQrPollResult>()
        var generated = 0
        val polled = mutableListOf<String>()
        val model = VisionLoginViewModel(gateway(
            generate = { generated++; CoreQrCode("https://qr/$generated", "$generated") },
            poll = { key ->
                polled += key
                if (key == "1") withContext(NonCancellable) { old.await() }
                else CoreQrPollResult(CoreQrPollStatus.EXPIRED, "过期")
            },
        ))
        model.start()
        advanceTimeBy(2000); runCurrent()
        model.refresh(); runCurrent()
        old.complete(CoreQrPollResult(CoreQrPollStatus.SUCCESS, "旧请求", "old", 1))
        runCurrent()
        assertEquals("2", model.state.value.code?.qrcodeKey)
        assertNull(model.state.value.result)
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf("1", "2"), polled)
        assertEquals(CoreQrPollStatus.EXPIRED, model.state.value.status)
        model.stop()
    }

    @Test fun stopCancelsPollingAndResumeKeepsCurrentQr() = runTest(dispatcher) {
        var polls = 0
        var generates = 0
        val model = VisionLoginViewModel(gateway(
            { generates++; CoreQrCode("https://qr", "key") },
            { polls++; CoreQrPollResult(CoreQrPollStatus.WAITING_SCAN, "等待") },
        ))
        model.start(); runCurrent()
        model.stop(); advanceTimeBy(5000); runCurrent()
        assertEquals(0, polls)
        model.start(); advanceTimeBy(2000); runCurrent()
        assertEquals(1, polls)
        assertEquals(1, generates)
        model.stop()
    }

    @Test fun failedRefreshClearsOldQrAndNeverPollsIt() = runTest(dispatcher) {
        var generates = 0
        var polls = 0
        val model = VisionLoginViewModel(gateway(
            { if (++generates > 1) error("offline") else CoreQrCode("https://qr", "key") },
            { polls++; CoreQrPollResult(CoreQrPollStatus.WAITING_SCAN, "等待") },
        ))
        model.start(); runCurrent()
        model.refresh(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertNull(model.state.value.code)
        assertFalse(model.state.value.loading)
        assertEquals(CoreQrPollStatus.ERROR, model.state.value.status)
        assertEquals(0, polls)
        model.stop()
    }

    @Test fun successStopsPollingWithoutFetchingProfile() = runTest(dispatcher) {
        var polls = 0
        val model = VisionLoginViewModel(gateway(
            { CoreQrCode("https://qr", "key") },
            { polls++; CoreQrPollResult(CoreQrPollStatus.SUCCESS, "成功", "DedeUserID=123; SESSDATA=fake", 123) },
        ))
        model.start(); advanceTimeBy(2000); runCurrent()
        assertEquals(123L, model.state.value.result?.mid)
        advanceTimeBy(6000); runCurrent()
        model.start(); runCurrent()
        assertEquals(1, polls)
        model.stop()
    }
}
