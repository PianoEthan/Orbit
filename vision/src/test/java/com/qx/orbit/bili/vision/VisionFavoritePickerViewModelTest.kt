package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionFavoritePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val folder = CoreFavFolder(99, "收藏夹", 1, "")
    private fun gateway(load: suspend (String) -> List<CoreFavFolder>) = object : UserDataGateway {
        override suspend fun loadHistory(cookie: String) = emptyList<CoreHistoryItem>()
        override suspend fun loadWatchLater(cookie: String) = emptyList<CoreVideo>()
        override suspend fun loadFavFolders(cookie: String, mid: Long) = load(cookie)
        override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int) = emptyList<CoreVideo>()
        override suspend fun loadDynamicVideos(cookie: String, offset: String) = emptyList<CoreVideo>()
    }

    @Test fun requiresExplicitSelectionAndReloadClearsOldChoice() = runTest(dispatcher) {
        val model = VisionFavoritePickerViewModel(gateway { listOf(folder) })
        model.load("account", 1); advanceUntilIdle()
        assertNull(model.state.value.selected)
        model.select(100)
        assertNull(model.state.value.selected)
        model.select(99)
        assertEquals(99L, model.state.value.selected)
        model.load("new-account", 2); advanceUntilIdle()
        assertNull(model.state.value.selected)
    }

    @Test fun lateResponseCannotRestoreClosedOrChangedAccountPicker() = runTest(dispatcher) {
        val old = CompletableDeferred<List<CoreFavFolder>>()
        val model = VisionFavoritePickerViewModel(gateway { if (it == "old") withContext(NonCancellable) { old.await() } else emptyList() })
        model.load("old", 1); runCurrent()
        model.load("new", 2); runCurrent()
        old.complete(listOf(folder)); advanceUntilIdle()
        assertTrue(model.state.value.folders.isEmpty())
        model.stop()
        assertNull(model.state.value.selected)
    }

    @Test fun closingPickerDiscardsPendingFolderResponse() = runTest(dispatcher) {
        val response = CompletableDeferred<List<CoreFavFolder>>()
        val model = VisionFavoritePickerViewModel(gateway { withContext(NonCancellable) { response.await() } })
        model.load("account", 1); runCurrent()
        model.stop()
        response.complete(listOf(folder)); advanceUntilIdle()
        assertTrue(model.state.value.folders.isEmpty())
        assertFalse(model.state.value.loading)
        assertNull(model.state.value.selected)
    }

    @Test fun loadingFailureAllowsRetryWithoutAnyWrite() = runTest(dispatcher) {
        var fail = true
        val model = VisionFavoritePickerViewModel(gateway { if (fail) error("offline") else listOf(folder) })
        model.load("account", 1); advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.loading)
        fail = false; model.load("account", 1); advanceUntilIdle()
        assertEquals(listOf(folder), model.state.value.folders)
        assertNull(model.state.value.error)
    }
}
