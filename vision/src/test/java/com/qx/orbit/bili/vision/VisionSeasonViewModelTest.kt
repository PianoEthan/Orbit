package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionSeasonViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val season = CoreSeason(42, 43, "番剧", "", "全两集", "")
    private val first = CoreVideo(1, "BV1", "第一集", "", "", cid = 101, episodeId = 11, seasonId = 42)
    private val second = first.copy(id = 2, bvid = "BV2", title = "第二集", cid = 102, episodeId = 12)
    private fun detail(followed: Boolean = false) = CoreSeasonDetail(season, "简介", listOf(CoreSeasonEpisode(11, "第一集", "", first), CoreSeasonEpisode(12, "第二集", "", second)), emptyList(), followed, 12, 25000)
    private open inner class Gateway : SeasonGateway {
        override suspend fun loadCatalog(type: Int, page: Int, cookie: String) = CoreSeasonPage(listOf(season), false)
        override suspend fun loadFollowed(mid: Long, type: Int, page: Int, cookie: String) = CoreSeasonPage(listOf(season), false)
        override suspend fun loadSeason(seasonId: Long, cookie: String) = detail()
        override suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String) = Unit
    }

    @Test fun retriesSameCatalogPageAndSwitchingTypeDropsLateResult() = runTest(dispatcher) {
        val pages = mutableListOf<Int>()
        var fail = true
        val pending = CompletableDeferred<CoreSeasonPage>()
        val model = VisionSeasonViewModel(object : Gateway() {
            override suspend fun loadCatalog(type: Int, page: Int, cookie: String): CoreSeasonPage {
                pages += page
                if (type == 1) return withContext(NonCancellable) { pending.await() }
                if (fail) error("offline")
                return CoreSeasonPage(listOf(season.copy(id = 99)), false)
            }
        })
        model.openCatalog(1, false, 0, ""); runCurrent()
        model.openCatalog(2, false, 0, ""); runCurrent()
        assertNotNull(model.state.value.error)
        fail = false; model.retry(); runCurrent()
        pending.complete(CoreSeasonPage(listOf(season), true)); advanceUntilIdle()
        assertEquals(listOf(1, 1, 1), pages)
        assertEquals(99L, model.state.value.items.single().id)
    }

    @Test fun accountSwitchClearsOldFollowStateAndCoalescesWrites() = runTest(dispatcher) {
        val write = CompletableDeferred<Unit>()
        var writes = 0
        val model = VisionSeasonViewModel(object : Gateway() {
            override suspend fun loadSeason(seasonId: Long, cookie: String) = detail(cookie == "new")
            override suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String) {
                writes++
                withContext(NonCancellable) { write.await() }
            }
        })
        model.openDetail(42, "old"); advanceUntilIdle()
        model.changeFollow("csrf"); model.changeFollow("csrf"); runCurrent()
        assertEquals(1, writes)
        model.openDetail(42, "new"); runCurrent()
        assertFalse(model.state.value.pending)
        write.complete(Unit); advanceUntilIdle()
        assertTrue(model.state.value.detail!!.followed)
        assertNull(model.state.value.message)
    }

    @Test fun playerEpisodeSelectionReturnsFullIdentityInsteadOfOnlyCid() = runTest(dispatcher) {
        val videos = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails = error("Must use season API")
        }
        val model = VisionPlayerEpisodesViewModel(videos, Gateway())
        model.open(first); advanceUntilIdle()
        assertEquals(listOf(101L, 102L), model.state.value.pages.map { it.cid })
        assertEquals(second, model.episodeVideo(102))
    }
}
