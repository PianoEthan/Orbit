package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ImageViewerGeometryTest {

    @Test
    fun fittedSizeMaintainsAspectRatioWideImage() {
        val viewport = IntSize(400, 400)
        val image = IntSize(1920, 1080)
        val fitted = calculateFittedImageSize(viewport, image)
        assertEquals(400f, fitted.width, 0.01f)
        assertEquals(400f * (1080f / 1920f), fitted.height, 0.01f)
    }

    @Test
    fun fittedSizeMaintainsAspectRatioTallImage() {
        val viewport = IntSize(400, 400)
        val image = IntSize(1080, 1920)
        val fitted = calculateFittedImageSize(viewport, image)
        assertEquals(400f * (1080f / 1920f), fitted.width, 0.01f)
        assertEquals(400f, fitted.height, 0.01f)
    }

    @Test
    fun fittedSizeHandlesEmptyOrZeroDimensions() {
        val fitted = calculateFittedImageSize(IntSize(0, 0), IntSize(100, 100))
        assertEquals(0f, fitted.width, 0.01f)
        assertEquals(0f, fitted.height, 0.01f)
    }

    @Test
    fun roundScreenScaleFitsDiagonalInsideDiameter() {
        val viewport = IntSize(400, 400)
        val image = IntSize(400, 400)
        val fitted = calculateFittedImageSize(viewport, image)
        val scale = calculateInitialRoundScale(isRoundScreen = true, viewportSize = viewport, fittedSize = fitted)
        val expectedScale = 400f / hypot(400f, 400f)
        assertEquals(expectedScale, scale, 0.01f)
        assertTrue(scale < 1.0f)
    }

    @Test
    fun squareScreenDoesNotApplyDiagonalScale() {
        val viewport = IntSize(400, 400)
        val image = IntSize(400, 400)
        val fitted = calculateFittedImageSize(viewport, image)
        val scale = calculateInitialRoundScale(isRoundScreen = false, viewportSize = viewport, fittedSize = fitted)
        assertEquals(1.0f, scale, 0.01f)
    }

    @Test
    fun translationClampsWithinViewportBoundariesWhenZoomedIn() {
        val viewportWidth = 400f
        val viewportHeight = 400f
        val scale = 2.0f
        val maxOffset = (scale - 1f) * 400f / 2f // 200f

        val inside = Offset(50f, -50f)
        val clampedInside = clampTranslation(inside, scale, viewportWidth, viewportHeight)
        assertEquals(50f, clampedInside.x, 0.01f)
        assertEquals(-50f, clampedInside.y, 0.01f)

        val outside = Offset(350f, -500f)
        val clampedOutside = clampTranslation(outside, scale, viewportWidth, viewportHeight)
        assertEquals(maxOffset, clampedOutside.x, 0.01f)
        assertEquals(-maxOffset, clampedOutside.y, 0.01f)
    }

    @Test
    fun translationResetsToZeroWhenScaleIsOneOrLess() {
        val offset = Offset(100f, 100f)
        assertEquals(Offset.Zero, clampTranslation(offset, 1.0f, 400f, 400f))
        assertEquals(Offset.Zero, clampTranslation(offset, 0.9f, 400f, 400f))
    }
}
