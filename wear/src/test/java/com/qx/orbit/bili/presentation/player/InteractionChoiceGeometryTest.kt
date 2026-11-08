package com.qx.orbit.bili.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractionChoiceGeometryTest {
    @Test fun letterboxUsesActualContentInsteadOfScreenBounds() {
        val content = interactionContentSize(200f, 200f, 1920f, 1080f)
        assertEquals(200f, content.width, 0.001f)
        assertEquals(112.5f, content.height, 0.001f)
        val bottom = interactionAnchor(960.0, 0.0, 1920, 1080, content.width, content.height)!!
        assertEquals(100f, bottom.x, 0.001f)
        assertEquals(112.5f, bottom.y, 0.001f)
        val top = interactionAnchor(0.0, 1080.0, 1920, 1080, content.width, content.height)!!
        assertEquals(0f, top.x, 0f)
        assertEquals(0f, top.y, 0f)
    }

    @Test fun portraitAndSwappedLayoutUseTheirOwnContentSpace() {
        val content = interactionContentSize(300f, 200f, 100f, 200f)
        assertEquals(100f, content.width, 0f)
        assertEquals(200f, content.height, 0f)
        val point = interactionAnchor(25.0, 150.0, 100, 200, content.width, content.height)!!
        assertEquals(25f, point.x, 0f)
        assertEquals(50f, point.y, 0f)
    }

    @Test fun missingInvalidCoordinatesRequestListInsteadOfInventedAnchors() {
        assertNull(interactionAnchor(null, 0.0, 10, 10, 100f, 100f))
        assertNull(interactionAnchor(0.0, 0.0, 0, 10, 100f, 100f))
        assertNull(interactionAnchor(Double.NaN, 0.0, 10, 10, 100f, 100f))
        assertNull(interactionAnchor(-1.0, 0.0, 10, 10, 100f, 100f))
        assertNull(interactionAnchor(0.0, 11.0, 10, 10, 100f, 100f))
    }
}
