package com.qx.orbit.bili.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.qx.orbit.bili.util.AppConfig
import com.qx.orbit.bili.util.ScreenMode

val LocalScreenRound = staticCompositionLocalOf { false }

@Composable
fun OrbitTheme(
    colorScheme: ColorScheme = dynamicColorScheme(LocalContext.current) ?: wearColorScheme,
    content: @Composable () -> Unit
) {
    val isRound = when (AppConfig.screenMode) {
        ScreenMode.AUTO -> LocalConfiguration.current.isScreenRound
        ScreenMode.ROUND -> true
        ScreenMode.SQUARE -> false
    }
    val dpiScale = AppConfig.dpiScale
    val originalDensity = LocalDensity.current
    val scaledDensity = remember(originalDensity, dpiScale) {
        Density(
            density = originalDensity.density * dpiScale,
            fontScale = originalDensity.fontScale * dpiScale
        )
    }
    CompositionLocalProvider(
        LocalScreenRound provides isRound,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
