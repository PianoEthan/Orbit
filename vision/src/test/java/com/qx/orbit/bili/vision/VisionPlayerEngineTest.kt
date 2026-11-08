package com.qx.orbit.bili.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPlayerEngineTest {
    @Test fun arm64AndArm32UseIjkWhileUnsupportedArchitecturesUseMedia3() {
        assertTrue(supportsVisionIjk(listOf("arm64-v8a")))
        assertTrue(supportsVisionIjk(listOf("armeabi-v7a")))
        assertFalse(supportsVisionIjk(listOf("x86_64")))
        assertEquals(VisionPlayerEngine.IJK, effectiveVisionEngine(VisionPlayerEngine.IJK, listOf("arm64-v8a")))
        assertEquals(VisionPlayerEngine.MEDIA3, effectiveVisionEngine(VisionPlayerEngine.IJK, listOf("x86_64")))
        assertEquals(VisionPlayerEngine.MEDIA3, effectiveVisionEngine(VisionPlayerEngine.MEDIA3, listOf("arm64-v8a")))
    }
}
