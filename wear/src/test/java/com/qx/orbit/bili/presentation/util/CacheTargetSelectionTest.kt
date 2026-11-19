package com.qx.orbit.bili.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheTargetSelectionTest {
    @Test
    fun emptyCidListFallsBackToFirstPage() {
        assertEquals(0, currentCachePageIndex(emptyList(), 123L))
        assertEquals(0, currentCachePageIndex(emptyList(), null))
    }

    @Test
    fun missingHistoryFallsBackToFirstPage() {
        assertEquals(0, currentCachePageIndex(listOf(11L, 22L, 33L), null))
    }

    @Test
    fun historyCidSelectsItsOwnPage() {
        assertEquals(2, currentCachePageIndex(listOf(11L, 22L, 33L), 33L))
        assertEquals(1, currentCachePageIndex(listOf(11L, 22L, 33L), 22L))
        assertEquals(0, currentCachePageIndex(listOf(11L, 22L, 33L), 11L))
    }

    @Test
    fun historyFromAnotherEpisodeDoesNotEscapeTheList() {
        // 看过合集里的另一集时，历史 cid 不在本视频的分P里
        assertEquals(0, currentCachePageIndex(listOf(11L, 22L), 999L))
    }
}
