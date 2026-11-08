package com.qx.orbit.bili.presentation.player

import android.view.ScaleGestureDetector
import android.view.View

class ViewScaleGestureListener(
    private val targetView: View
) : ScaleGestureDetector.OnScaleGestureListener {

    private var scaleFactor = 1.0f
    var scaling = false
        private set
    var canReset = false
        private set

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        canReset = true
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = scaleFactor.coerceIn(1.0f, 5.0f)
        targetView.scaleX = scaleFactor
        targetView.scaleY = scaleFactor
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaling = false
    }

    fun resetScale() {
        scaleFactor = 1.0f
        targetView.scaleX = 1.0f
        targetView.scaleY = 1.0f
        canReset = false
    }
}
