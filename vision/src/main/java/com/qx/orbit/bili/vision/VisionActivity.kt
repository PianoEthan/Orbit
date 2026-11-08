package com.qx.orbit.bili.vision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class VisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VisionPreferences.init(this)
        VisionSearchHistory.init(this)
        VisionUserSession.init(this)
        enableEdgeToEdge()
        setContent {
            VisionTheme { VisionNavigation() }
        }
    }
}
