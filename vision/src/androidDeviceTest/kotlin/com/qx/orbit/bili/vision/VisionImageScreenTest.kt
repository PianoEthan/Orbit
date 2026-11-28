package com.qx.orbit.bili.vision

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.test.espresso.Espresso
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VisionImageScreenTest {
    @get:Rule val compose = createComposeRule()
    private val failImages = AtomicBoolean(false)
    private val sources = listOf("https://image.test/one.png", "https://image.test/two.png", "https://image.test/three.png")

    @Before fun mockImages() {
        failImages.set(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF4477AA.toInt())
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        val bytes = output.toByteArray()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (failImages.get()) 503 else 200).message("response")
                .body(bytes.toResponseBody()).build()
        }.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .build(),
        )
    }

    private fun image(page: Int) = compose.onNodeWithContentDescription("第 $page 张图片，共 3 张")

    @Test fun remoteArrowsChangePagesButPanWhileZoomedAndBackResetsBeforeLeaving() {
        var exits = 0
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            VisionTheme { VisionImageScreen(sources, 0) { exits++ } }
        }
        compose.waitForIdle()
        compose.onNodeWithText("放大图片").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        image(2).assertExists()
        compose.onNodeWithText("放大图片").performClick()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        image(2).assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("放大图片").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, exits) }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun failedImageCanRetryWithoutLosingPage() {
        failImages.set(true)
        compose.setContent { VisionTheme { VisionImageScreen(sources, 1) {} } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("重试图片").fetchSemanticsNodes().isNotEmpty() }
        failImages.set(false)
        compose.onNodeWithText("重试图片").printToLog("RETRY_BUTTON")
        compose.onNodeWithText("重试图片").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("图片加载失败").fetchSemanticsNodes().isEmpty() }
        image(2).assertExists()
    }

    @Test fun doubleTapZoomsAndSecondDoubleTapResets() {
        compose.setContent { VisionTheme { VisionImageScreen(sources, 0) {} } }
        image(1).performTouchInput { doubleClick() }
        compose.onNodeWithText("还原图片").assertIsDisplayed()
        image(1).performTouchInput { doubleClick() }
        compose.onNodeWithText("放大图片").assertIsDisplayed()
    }

    @Test fun pinchZoomAndPageButtonsResetTransform() {
        compose.setContent { VisionTheme { VisionImageScreen(sources, 0) {} } }
        image(1).performTouchInput {
            pinch(Offset(center.x - 30, center.y), Offset(center.x + 30, center.y),
                Offset(center.x - 100, center.y), Offset(center.x + 100, center.y))
        }
        compose.onNodeWithText("还原图片").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一张").performClick()
        image(2).assertExists()
        compose.onNodeWithText("放大图片").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一张").performClick()
        image(1).assertExists()
    }

    @Test fun pageAndZoomSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { VisionTheme { VisionImageScreen(sources, 1) {} } }
        compose.onNodeWithContentDescription("下一张").performClick()
        image(3).assertExists()
        compose.onNodeWithText("放大图片").performClick()
        restoration.emulateSavedInstanceStateRestore()
        image(3).assertExists()
        compose.onNodeWithText("还原图片").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一张").assertIsNotEnabled()
    }
}
