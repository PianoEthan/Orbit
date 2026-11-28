package com.qx.orbit.bili.vision

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.nio.file.Path
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val smokeTest = "--smoke-test" in args
    if (!smokeTest) {
        val directory = Path.of(System.getProperty("user.home"), ".orbit-vision")
        initializeVisionStores { name -> DesktopVisionStore(directory, name) }
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Orbit Vision",
            state = rememberWindowState(width = 1200.dp, height = 760.dp),
        ) {
            val viewModels = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(viewModels) { onDispose { viewModels.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModels) { VisionApp() }
            if (smokeTest) {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    exitApplication()
                }
            }
        }
    }
}

@Composable
internal actual fun visionIsTv(): Boolean = false

internal actual val visionIsDesktop: Boolean = true

@Composable
internal actual fun visionWindowWidthDp(): Int = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp().value.toInt()
}

@Composable
internal actual fun visionDynamicColorsAvailable(): Boolean = false

@Composable
internal actual fun visionDynamicColors(dark: Boolean): ColorScheme? = null

internal actual fun visionIsRepeatedKey(event: KeyEvent): Boolean = false

internal actual fun visionElapsedTimeMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
