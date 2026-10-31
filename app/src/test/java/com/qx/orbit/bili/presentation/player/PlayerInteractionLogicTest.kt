package com.qx.orbit.bili.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInteractionLogicTest {
    @Test
    fun nearBarUsesFixedSecondsAndDistanceOnlyIncreasesTheWindow() {
        assertEquals(30_000.0, distanceSeekWindowMs(7_200_000L, 0f), 0.001)
        assertEquals(7_200_000.0, distanceSeekWindowMs(7_200_000L, 120f), 0.001)
        assertTrue(distanceSeekWindowMs(7_200_000L, 20f) < distanceSeekWindowMs(7_200_000L, 80f))
        assertEquals(10_000.0, distanceSeekWindowMs(10_000L, 0f), 0.001)
        assertEquals(0.0, distanceSeekWindowMs(0L, 100f), 0.001)
    }

    @Test
    fun verticalMovementNeverChangesTheSeekTarget() {
        val session = DistanceSeekSession(7_200_000L, 3_600_000L, 240f)
        assertEquals(3_600_000L, session.drag(0f, 0f))
        assertEquals(3_600_000L, session.drag(0f, 120f))
        assertEquals(3_600_000L, session.drag(0f, 5f))
    }

    @Test
    fun tinyDeltasAccumulateAndSeekClampsAtBothEnds() {
        val session = DistanceSeekSession(120_000L, 60_000L, 300f)
        repeat(100) { session.drag(0.001f, 0f) }
        assertEquals(60_010L, session.targetMs)
        assertEquals(120_000L, session.drag(100_000f, 120f))
        assertEquals(0L, session.drag(-100_000f, 120f))
    }

    @Test
    fun changingDistanceDoesNotRecalculatePreviousHorizontalMovement() {
        val session = DistanceSeekSession(3_600_000L, 1_000_000L, 300f)
        assertEquals(1_001_000L, session.drag(10f, 0f))
        assertEquals(1_001_000L, session.drag(0f, 120f))
        assertEquals(1_121_000L, session.drag(10f, 120f))
    }

    @Test
    fun secondTapVerticalDragOwnsZoomEvenWhenPanAndSeekAreAvailable() {
        assertEquals(PlayerDragGesture.Zoom, drag(1f, 20f, second = true))
        assertEquals(PlayerDragGesture.Seek, drag(20f, 1f))
        assertEquals(PlayerDragGesture.Pan, drag(1f, 20f))
        assertEquals(PlayerDragGesture.None, drag(2f, 2f, second = true))
    }

    @Test
    fun disablingEitherZoomSwitchCancelsDoubleTapDrag() {
        assertEquals(PlayerDragGesture.Cancel, drag(1f, 20f, second = true, enabled = false))
        assertEquals(
            PlayerDragGesture.Cancel,
            playerDragGesture(1f, 20f, 8f, true, true, false, true, true)
        )
        assertEquals(PlayerDragGesture.Cancel, drag(20f, 1f, second = true))
    }

    @Test
    fun zoomIsReversibleAndIndependentOfPixelDensity() {
        val down = oneFingerZoomFactor(80f)
        val up = oneFingerZoomFactor(-80f)
        assertEquals(1f, down * up, 0.00001f)
        assertEquals(oneFingerZoomFactor(80f), oneFingerZoomFactor(160f / 2f), 0.00001f)
    }

    @Test
    fun everyDanmakuGateMustBeSatisfied() {
        assertTrue(shouldRunDanmaku(true, true, true, true, false, false))
        assertFalse(shouldRunDanmaku(false, true, true, true, false, false))
        assertFalse(shouldRunDanmaku(true, false, true, true, false, false))
        assertFalse(shouldRunDanmaku(true, true, false, true, false, false))
        assertFalse(shouldRunDanmaku(true, true, true, false, false, false))
        assertFalse(shouldRunDanmaku(true, true, true, true, true, false))
        assertFalse(shouldRunDanmaku(true, true, true, true, false, true))
    }

    @Test
    fun transformPointerOwnershipSurvivesOriginalFingerReplacement() {
        val pointers = PlayerPointerSequence(1L)
        pointers.update(listOf(1L, 2L))
        assertTrue(pointers.isTransform)
        pointers.update(listOf(2L))
        assertEquals(2L, pointers.activePointer)
        pointers.update(listOf(2L, 3L))
        assertEquals(2L, pointers.activePointer)
        pointers.update(listOf(3L))
        assertEquals(3L, pointers.activePointer)
        assertTrue(pointers.isTransform)
        pointers.update(emptyList())
        assertEquals(null, pointers.activePointer)
        assertTrue(pointers.isTransform)
    }

    @Test
    fun transformOwnershipDoesNotResetWhenTheOriginalFingerDisappearsInOneEvent() {
        val pointers = PlayerPointerSequence(1L)
        pointers.update(listOf(1L, 2L))
        pointers.update(listOf(2L, 3L))
        assertEquals(2L, pointers.activePointer)
        assertTrue(pointers.isTransform)
    }

    private fun drag(dx: Float, dy: Float, second: Boolean = false, enabled: Boolean = true) =
        playerDragGesture(dx, dy, 8f, second, enabled, true, true, true)
}
