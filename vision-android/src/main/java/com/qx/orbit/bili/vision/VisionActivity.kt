package com.qx.orbit.bili.vision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class VisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeVisionAndroid(this)
        enableEdgeToEdge()
        setContent {
            VisionApp()
        }
    }
}
