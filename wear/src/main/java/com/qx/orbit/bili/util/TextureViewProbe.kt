package com.qx.orbit.bili.util

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.widget.FrameLayout

object TextureViewProbe {
    private const val TIMEOUT_MS = 3000L

    fun probe(
        container: FrameLayout,
        onResult: (supported: Boolean) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var settled = false

        val timeoutRunnable = Runnable {
            if (!settled) {
                settled = true
                onResult(false)
            }
        }

        val probeView = TextureView(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                    if (!settled) {
                        settled = true
                        handler.removeCallbacks(timeoutRunnable)
                        surface.release()
                        onResult(true)
                    }
                }

                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            }
        }

        container.addView(probeView)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }
}
