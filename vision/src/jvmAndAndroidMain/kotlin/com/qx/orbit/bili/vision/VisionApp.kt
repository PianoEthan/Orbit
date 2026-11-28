package com.qx.orbit.bili.vision

import androidx.compose.runtime.Composable

@Composable
fun VisionApp() {
    VisionTheme { VisionNavigation() }
}

internal fun initializeVisionStores(openStore: (String) -> VisionStore) {
    VisionPreferences.init(openStore)
    VisionSearchHistory.init(openStore)
    VisionUserSession.init(openStore)
}
