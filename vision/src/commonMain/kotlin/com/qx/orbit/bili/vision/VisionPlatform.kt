package com.qx.orbit.bili.vision

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler as ComposeBackHandler
import androidx.compose.ui.input.key.KeyEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    ComposeBackHandler(enabled, onBack)
}

@Composable
internal expect fun visionIsTv(): Boolean

internal expect val visionIsDesktop: Boolean

@Composable
internal expect fun visionWindowWidthDp(): Int

@Composable
internal expect fun visionDynamicColorsAvailable(): Boolean

@Composable
internal expect fun visionDynamicColors(dark: Boolean): ColorScheme?

internal expect fun visionIsRepeatedKey(event: KeyEvent): Boolean

internal expect fun visionElapsedTimeMs(): Long

internal interface VisionStore {
    fun getString(key: String, default: String): String
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float
    fun put(vararg values: Pair<String, Any>)
    fun remove(key: String)
    fun clear()
}
