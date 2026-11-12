package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreCreator
import com.qx.orbit.bili.core.api.CoreCreatorVideos
import com.qx.orbit.bili.core.api.CreatorGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisionCreatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun creator(mid: Long) = CoreCreator(mid, "UP$mid", "", "", 10, 1, false)

    @Test fun accountChangeDiscardsOldProfileAndOldWriteResult() = runTest(dispatcher) {
        val old = CompletableDeferred<CoreCreator>()
        val gateway = object : CreatorGateway {
            override suspend fun loadCreator(mid: Long, cookie: String): CoreCreator =
                if (cookie == "old") withContext(NonCancellable) { old.await() } else creator(mid)
            override suspend fun loadVideos(mid: Long, page: Int, cookie: String) = CoreCreatorVideos(emptyList(), false)
            override suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String) = Unit
        }
        val model = VisionCreatorViewModel(gateway)
        model.open(1, "old"); runCurrent()
        model.open(2, "new"); runCurrent()
        old.complete(creator(1)); advanceUntilIdle()
        assertEquals(2L, model.state.value.creator?.mid)
        assertFalse(model.state.value.loading)
    }

    @Test fun profileFailureDoesNotHideVideosAndFailedPageRetriesSamePage() = runTest(dispatcher) {
        val requested = mutableListOf<Int>()
        var fail = true
        val gateway = object : CreatorGateway {
            override suspend fun loadCreator(mid: Long, cookie: String): CoreCreator = error("profile offline")
            override suspend fun loadVideos(mid: Long, page: Int, cookie: String): CoreCreatorVideos {
                requested += page
                if (fail) error("videos offline")
                return CoreCreatorVideos(emptyList(), false)
            }
            override suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String) = Unit
        }
        val model = VisionCreatorViewModel(gateway)
        model.open(1, ""); advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertNotNull(model.state.value.videosError)
        fail = false
        model.loadVideos(); advanceUntilIdle()
        assertEquals(listOf(1, 1), requested)
        assertNull(model.state.value.videosError)
        assertNotNull(model.state.value.error)
    }
}
