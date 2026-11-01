package com.qx.orbit.bili.presentation.player

import com.qx.orbit.bili.data.model.InteractionChoice
import com.qx.orbit.bili.data.model.InteractionEdge
import com.qx.orbit.bili.data.model.InteractionHiddenVar
import com.qx.orbit.bili.data.model.InteractionQuestion
import com.qx.orbit.bili.data.model.InteractionStoryNode
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.PlayerInteraction
import com.qx.orbit.bili.data.model.PlayerMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.qx.orbit.bili.data.interaction.InteractionProgressStore

class InteractionPlaybackCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()
    private val info = PlayerMetadata(interaction = PlayerInteraction(1))
    private val source = PlayerData(aid = 5, cid = 10, videoUrl = "stale", audioUrl = "stale")
    private fun node(edge: Long = 1, question: InteractionQuestion = InteractionQuestion(
        type = 1, choices = listOf(InteractionChoice(id = 1, cid = 10, nativeAction = "v=v+1", option = "again"))
    )) = InteractionVideo(
        edgeId = edge, storyList = listOf(InteractionStoryNode(edgeId = edge, cid = 10, isCurrent = 1)),
        edges = InteractionEdge(questions = listOf(question)),
        hiddenVars = listOf(InteractionHiddenVar(id = "v", value = 0.0, type = 1))
    )
    private class Host {
        var stops = 0
        val requests = mutableListOf<InteractionPlaybackCoordinator.MediaRequest>()
        fun coordinator(
            metadata: suspend (Long, Long) -> InteractionVideoResult<PlayerMetadata>,
            edge: suspend (PlayerData, Long, Long?) -> InteractionVideoResult<InteractionVideo>,
            store: InteractionProgressStore? = null,
            accountKey: Long = 0L
        ) = InteractionPlaybackCoordinator(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), metadata, edge,
            { stops++ }, { requests += it }, store = store, accountKeyProvider = { accountKey })

        suspend fun awaitRequest(): InteractionPlaybackCoordinator.MediaRequest {
            for (i in 0 until 50) {
                requests.lastOrNull()?.let { return it }
                kotlinx.coroutines.delay(20)
            }
            return requests.last()
        }

        suspend fun awaitNextRequest(previousCount: Int): InteractionPlaybackCoordinator.MediaRequest {
            for (i in 0 until 50) {
                if (requests.size > previousCount) return requests.last()
                kotlinx.coroutines.delay(20)
            }
            return requests.last()
        }
    }

    @Test fun ordinaryVideoWithoutHintStillProbesAndNeverRequestsEdge() {
        val host = Host()
        var probes = 0
        val coordinator = host.coordinator({ _, _ -> probes++; InteractionVideoResult.Success(PlayerMetadata()) },
            { _, _, _ -> error("ordinary video must not request edge") })
        coordinator.open(source)
        assertEquals(1, probes)
        assertFalse(coordinator.state.value.interactive)
        assertFalse(coordinator.state.value.blocksEpisodes)
        assertEquals(source, host.requests.single().data)
        assertTrue(coordinator.mediaPrepared(host.requests.single()))
    }

    @Test fun sameCidVisitsPrepareAgainAndRetriesDoNotRepeatAction() {
        val host = Host()
        var failTarget = false
        val coordinator = host.coordinator({ _, _ -> InteractionVideoResult.Success(info) }, { _, _, _ ->
            if (failTarget) InteractionVideoResult.ApiError(-1, "offline") else InteractionVideoResult.Success(node())
        })
        coordinator.open(source)
        val rootRequest = host.requests.last()
        assertEquals("", rootRequest.data.videoUrl)
        assertEquals("", rootRequest.data.audioUrl)
        assertNull(rootRequest.data.dashData)
        assertNull(coordinator.state.value.session.visitId)
        assertTrue(coordinator.mediaPrepared(rootRequest))
        assertFalse(coordinator.mediaPrepared(rootRequest))
        assertTrue(coordinator.mediaPrepared(rootRequest, reloading = true))
        assertEquals(1L, coordinator.state.value.session.visitId)
        coordinator.observe(100, 100, true, 0, true)
        val state = coordinator.state.value.session
        failTarget = true
        coordinator.choose(0, 0, state.visitId, state.generation)
        assertEquals(0.0, coordinator.state.value.session.variables["v"]!!, 0.0)
        assertNotNull(coordinator.state.value.error)
        failTarget = false
        coordinator.retry()
        val second = host.requests.last()
        assertNotEquals(rootRequest.token, second.token)
        assertEquals(rootRequest.data.cid, second.data.cid)
        assertEquals(0L, second.seekToMs)
        coordinator.mediaFailed(second, "decode failed")
        coordinator.retry()
        val retry = host.requests.last()
        assertNotEquals(second.token, retry.token)
        assertFalse(coordinator.mediaPrepared(second))
        assertTrue(coordinator.mediaPrepared(retry))
        assertEquals(2L, coordinator.state.value.session.visitId)
        assertEquals(1.0, coordinator.state.value.session.variables["v"]!!, 0.0)
        assertTrue(coordinator.state.value.blocksEpisodes)
        assertEquals(3, host.requests.size)
    }

    @Test fun failedProbeCannotFallBackToOldSourceAndCanRetry() {
        val host = Host()
        var fail = true
        val coordinator = host.coordinator({ _, _ ->
            if (fail) InteractionVideoResult.ApiError(-1, "offline") else InteractionVideoResult.Success(PlayerMetadata())
        }, { _, _, _ -> error("not interactive") })
        coordinator.open(source)
        assertTrue(host.requests.isEmpty())
        assertTrue(coordinator.state.value.blocksControls)
        assertTrue(host.stops > 0)
        fail = false
        coordinator.retry()
        assertEquals(1, host.requests.size)
    }

    @Test fun oldMetadataAndCallbacksCannotPublishAfterNewSource() = runBlocking {
        val host = Host()
        val deferred = CompletableDeferred<InteractionVideoResult<PlayerMetadata>>()
        val coordinator = host.coordinator({ aid, _ ->
            if (aid == 5L) deferred.await() else InteractionVideoResult.Success(PlayerMetadata())
        }, { _, _, _ -> error("stale graph") })
        coordinator.open(source)
        coordinator.open(source.copy(aid = 6))
        deferred.complete(InteractionVideoResult.Success(info))
        assertEquals(1, host.requests.size)
        val request = host.requests.single()
        coordinator.open(source.copy(type = PlayerData.TYPE_LOCAL))
        assertFalse(coordinator.mediaPrepared(request))
    }

    @Test fun countdownFreezesOnBackgroundPauseAndBufferAndSeekDoesNotTimeout() {
        val host = Host()
        val question = InteractionQuestion(type = 4, startTime = 0, duration = 1000,
            choices = listOf(InteractionChoice(id = 1, cid = 10, isDefault = 1)))
        val coordinator = host.coordinator({ _, _ -> InteractionVideoResult.Success(info) },
            { _, _, _ -> InteractionVideoResult.Success(node(question = question)) })
        coordinator.open(source)
        coordinator.mediaPrepared(host.requests.last())
        coordinator.observe(10, 2000, false, 0, true)
        coordinator.observe(10, 2000, false, 5000, false)
        coordinator.observe(10, 2000, false, 6000, true)
        assertEquals(1000L, coordinator.state.value.session.question?.remainingMs)
        coordinator.observe(10, 2000, false, 6500, true)
        assertEquals(500L, coordinator.state.value.session.question?.remainingMs)
        coordinator.observe(10, 2000, false, 9000, true, discontinuity = true)
        assertEquals(500L, coordinator.state.value.session.question?.remainingMs)
        assertEquals(1, host.requests.size)
    }

    @Test fun graphInvalidAndNeedReloadRequireExplicitFreshRestart() {
        val host = Host()
        var infoResult = info.copy(interaction = PlayerInteraction(1, needReload = 1))
        val coordinator = host.coordinator({ _, _ -> InteractionVideoResult.Success(infoResult) },
            { _, _, _ -> InteractionVideoResult.Success(node()) })
        coordinator.open(source)
        assertTrue(coordinator.state.value.requiresRestart)
        coordinator.retry()
        assertTrue(host.requests.isEmpty())
        infoResult = info
        coordinator.restart()
        assertEquals(1, host.requests.size)
        coordinator.mediaPrepared(host.requests.last())
        coordinator.observe(100, 100, true, 0, true)
        assertTrue(coordinator.state.value.blocksControls)
    }

    @Test fun rootApiFailureRetriesWithoutPlayingOriginalCidAndGraphInvalidCannotReuseGraph() {
        val host = Host()
        var calls = 0
        var invalid = false
        val coordinator = host.coordinator({ _, _ -> InteractionVideoResult.Success(info) }, { _, _, _ ->
            calls++
            when {
                invalid -> InteractionVideoResult.GraphInvalid("expired")
                calls == 1 -> InteractionVideoResult.ApiError(-1, "offline")
                else -> InteractionVideoResult.Success(node())
            }
        })
        coordinator.open(source)
        assertTrue(host.requests.isEmpty())
        coordinator.retry()
        assertEquals(2, calls)
        val previous = host.requests.last()
        assertTrue(coordinator.mediaPrepared(previous))
        invalid = true
        coordinator.restart()
        assertTrue(coordinator.state.value.requiresRestart)
        assertTrue(coordinator.state.value.error!!.contains("99003"))
        assertFalse(coordinator.mediaPrepared(previous))
        val failedCalls = calls
        coordinator.retry()
        assertEquals(failedCalls, calls)
        invalid = false
        coordinator.restart()
        assertNotEquals(previous.epoch, host.requests.last().epoch)
    }

    @Test fun nodeMetadataGraphChangeStopsBeforeMediaAndRequestsExplicitRestart() {
        val host = Host()
        val coordinator = host.coordinator({ _, cid ->
            InteractionVideoResult.Success(if (cid == 10L) info else info.copy(interaction = PlayerInteraction(2)))
        }, { _, _, _ -> InteractionVideoResult.Success(node().copy(
            storyList = listOf(InteractionStoryNode(edgeId = 1, cid = 30, isCurrent = 1))
        )) })
        coordinator.open(source)
        assertTrue(host.requests.isEmpty())
        assertTrue(coordinator.state.value.requiresRestart)
        assertTrue(coordinator.state.value.error!!.contains("版本"))
    }

    @Test fun savedProgressResumesAtSavedNodeAndPositionAndRestartClearsIt() = runBlocking {
        val dir = temp.newFolder()
        val store = InteractionProgressStore(dir)
        val host = Host()
        val edge1 = node(1, InteractionQuestion(type = 1, choices = listOf(InteractionChoice(id = 2, cid = 20, nativeAction = "v=v+3", option = "to2"))))
        val edge2 = node(2, InteractionQuestion(type = 1, choices = listOf(InteractionChoice(id = 1, cid = 10, nativeAction = "v=v+1", option = "back"))))
        val coordinator = host.coordinator({ _, cid -> InteractionVideoResult.Success(if (cid == 10L) info else info.copy(interaction = PlayerInteraction(1))) }, { _, _, edgeId ->
            InteractionVideoResult.Success(if (edgeId == 2L) edge2 else edge1)
        }, store = store, accountKey = 1001L)

        coordinator.open(source)
        val req1 = host.awaitRequest()
        coordinator.mediaPrepared(req1)
        assertEquals(1L, coordinator.state.value.session.visitId)

        coordinator.observe(100, 100, true, 0, true)
        val state = coordinator.state.value.session
        coordinator.choose(0, 0, state.visitId, state.generation)
        val req2 = host.awaitNextRequest(1)
        coordinator.mediaPrepared(req2)
        assertEquals(2L, coordinator.state.value.session.edgeId)
        assertEquals(20L, coordinator.state.value.session.cid)
        assertEquals(3.0, coordinator.state.value.session.variables["v"]!!, 0.0)

        coordinator.closeAndSave(15_000L)

        val host2 = Host()
        val coordinator2 = host2.coordinator({ _, cid -> InteractionVideoResult.Success(if (cid == 10L) info else info.copy(interaction = PlayerInteraction(1))) }, { _, _, edgeId ->
            InteractionVideoResult.Success(if (edgeId == 2L) edge2 else edge1)
        }, store = store, accountKey = 1001L)

        coordinator2.open(source)
        val resumeReq = host2.awaitRequest()
        assertEquals(20L, resumeReq.data.cid)
        assertEquals(15_000L, resumeReq.seekToMs)
        coordinator2.mediaPrepared(resumeReq)
        assertEquals(2L, coordinator2.state.value.session.edgeId)
        assertEquals(3.0, coordinator2.state.value.session.variables["v"]!!, 0.0)

        coordinator2.restart()
        val restartReq = host2.awaitNextRequest(1)
        assertEquals(10L, restartReq.data.cid)
        assertEquals(0L, restartReq.seekToMs)
        coordinator2.mediaPrepared(restartReq)
        assertEquals(1L, coordinator2.state.value.session.edgeId)
        assertEquals(0.0, coordinator2.state.value.session.variables["v"]!!, 0.0)
    }
}
