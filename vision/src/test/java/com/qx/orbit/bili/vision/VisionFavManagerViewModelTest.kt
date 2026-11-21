package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreFavFolder
import com.qx.orbit.bili.core.api.CoreFavVideoPage
import com.qx.orbit.bili.core.api.CoreHistoryItem
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.UserDataGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionFavManagerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeGateway : UserDataGateway {
        var folders = mutableListOf(CoreFavFolder(1, "默认", 2, ""), CoreFavFolder(2, "其他", 0, ""))
        var videos = mutableListOf(CoreVideo(11, "BV11", "视频11", "", "UP"), CoreVideo(22, "BV22", "视频22", "", "UP"))
        var createResult = true
        var moveResult = true
        var movedAids: List<Long>? = null
        var movedFrom = 0L
        var movedTo = 0L
        override suspend fun loadHistory(cookie: String) = emptyList<CoreHistoryItem>()
        override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
        override suspend fun loadFavFolders(cookie: String, mid: Long) = folders.toList()
        override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = videos.toList()
        override suspend fun loadFavVideoPage(cookie: String, folderId: Long, page: Int) = CoreFavVideoPage(videos.toList(), false)
        override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
        override suspend fun createFavFolder(title: String, intro: String, privacy: Int, cookie: String, csrf: String): Boolean {
            if (createResult) folders.add(CoreFavFolder(3, title, 0, ""))
            return createResult
        }
        override suspend fun deleteFavFolder(mediaId: Long, cookie: String, csrf: String): Boolean {
            folders.removeAll { it.id == mediaId }
            return true
        }
        override suspend fun moveFavVideos(aids: List<Long>, fromFolder: Long, toFolder: Long, cookie: String, csrf: String): Boolean {
            movedAids = aids; movedFrom = fromFolder; movedTo = toFolder
            if (moveResult) videos.removeAll { it.id in aids }
            return moveResult
        }
    }

    @Test fun loadListsFolders() = runTest(dispatcher) {
        val viewModel = VisionFavManagerViewModel(FakeGateway())
        viewModel.load("session", 1)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.folders.size)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun createReloadsFoldersAndMessages() = runTest(dispatcher) {
        val viewModel = VisionFavManagerViewModel(FakeGateway())
        viewModel.load("session", 1)
        advanceUntilIdle()
        viewModel.create("新夹", "", 0, "csrf")
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.folders.size)
        assertEquals("收藏夹已创建", viewModel.state.value.message)
    }

    @Test fun deleteRemovesFolder() = runTest(dispatcher) {
        val viewModel = VisionFavManagerViewModel(FakeGateway())
        viewModel.load("session", 1)
        advanceUntilIdle()
        viewModel.delete(2, "csrf")
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.state.value.folders.map { it.id })
        assertEquals("收藏夹已删除", viewModel.state.value.message)
    }

    @Test fun moveSelectedVideosToTarget() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionFavManagerViewModel(gateway)
        viewModel.load("session", 1)
        advanceUntilIdle()
        viewModel.startMove(gateway.folders.first())
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.moveVideos.size)
        viewModel.toggle(11)
        viewModel.setTarget(2)
        viewModel.move("csrf")
        advanceUntilIdle()
        assertEquals(listOf(11L), gateway.movedAids)
        assertEquals(1L, gateway.movedFrom)
        assertEquals(2L, gateway.movedTo)
        assertTrue(viewModel.state.value.moveSelected.isEmpty())
        assertEquals("已移动 1 个视频", viewModel.state.value.message)
    }

    @Test fun moveIgnoredWithoutTarget() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val viewModel = VisionFavManagerViewModel(gateway)
        viewModel.load("session", 1)
        advanceUntilIdle()
        viewModel.startMove(gateway.folders.first())
        advanceUntilIdle()
        viewModel.toggle(11)
        viewModel.move("csrf")
        advanceUntilIdle()
        assertEquals(null, gateway.movedAids)
    }
}
