package com.qx.orbit.bili.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionLayoutPolicyTest {
    @Test fun userGridReducesColumnsAtLargeFontSizes() {
        assertEquals(2, visionUserGridColumns(380f, 1f))
        assertEquals(1, visionUserGridColumns(380f, 1.5f))
        assertEquals(1, visionUserGridColumns(380f, 2f))
        assertEquals(3, visionUserGridColumns(868f, 1f))
        assertEquals(2, visionUserGridColumns(868f, 1.5f))
        assertEquals(2, visionUserGridColumns(868f, 2f))
    }

    @Test
    fun gridKeepsPhoneCardsReadableAndExpandsOnLargeWindows() {
        assertEquals(1, visionGridColumns(320f))
        assertEquals(2, visionGridColumns(360f))
        assertEquals(2, visionGridColumns(480f))
        assertEquals(3, visionGridColumns(800f))
        assertEquals(4, visionGridColumns(1000f))
        assertEquals(6, visionGridColumns(1440f))
        assertEquals(6, visionGridColumns(3840f))
    }

    @Test
    fun listGridKeepsRowCellsWideEnoughForAvatarAndActions() {
        assertEquals(1, visionListColumns(599f))
        assertEquals(2, visionListColumns(600f))
        assertEquals(2, visionListColumns(1000f))
        assertEquals(3, visionListColumns(1440f))
        assertEquals(3, visionListColumns(3840f))
    }
}
