package com.qx.orbit.bili.vision

import org.junit.Assert.*
import org.junit.Test

class VisionImagePolicyTest {
    @Test fun sourcesOnlyAllowSecureImagesAndBoundSavedStateSize() {
        assertEquals(listOf("https://a", "https://b"), visionImageSources(listOf("https://a", "file:///private", "http://a", "https://a", "https://b")))
        assertEquals(100, visionImageSources((1..200).map { "https://image/$it" }).size)
    }
    @Test fun pagingClampsRestoredIndexAndNeverWrapsAtEdges() {
        assertEquals(0, visionImagePage(-1, 0, 3))
        assertEquals(2, visionImagePage(99, 0, 3))
        assertEquals(0, visionImagePage(0, -1, 3))
        assertEquals(2, visionImagePage(2, 1, 3))
        assertEquals(1, visionImagePage(0, 1, 3))
        assertEquals(0, visionImagePage(0, 1, 0))
    }
    @Test fun panCannotMoveImageOutOfViewportAndResetReturnsToOrigin() {
        assertEquals(100f, visionImagePan(500f, 200f, 2f), 0f)
        assertEquals(-100f, visionImagePan(-500f, 200f, 2f), 0f)
        assertEquals(0f, visionImagePan(100f, 200f, 1f), 0f)
        assertEquals(300f, visionImagePan(999f, 200f, 8f), 0f)
    }
}
