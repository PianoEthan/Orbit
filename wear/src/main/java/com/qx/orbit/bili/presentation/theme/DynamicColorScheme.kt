package com.qx.orbit.bili.presentation.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.score.Score
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ActiveDynamicTheme {
    var colorScheme by mutableStateOf<ColorScheme?>(null)
}

fun extractSeedColorFromBitmap(bitmap: Bitmap): Int? {
    return try {
        // 1. 获取图片的像素数组
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // 2. 量化像素并评分，提取出最具代表性的“种子色” (Seed Color Int)
        val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
        Score.score(quantizerResult).firstOrNull()
    } catch (e: Exception) {
        null
    }
}

fun generateWearColorSchemeFromSeed(seedColorInt: Int, fallbackColor: ColorScheme): ColorScheme {
    return try {
        // 3. 生成 MD3 色板
        // Wear OS 通常是深色模式，所以 isDark = true
        // SchemeContent 专为“基于图片内容取色”设计，能保证还原图片原有色相
        val hctColor = Hct.fromInt(seedColorInt)
        val dynamicScheme = SchemeContent(hctColor, true, 0.0)

        // 4. 映射到 Wear M3 的 ColorScheme
        ColorScheme(
            primary = Color(dynamicScheme.primary),
            onPrimary = Color(dynamicScheme.onPrimary),
            primaryDim = Color(dynamicScheme.primaryFixedDim),
            primaryContainer = Color(dynamicScheme.primaryContainer),
            onPrimaryContainer = Color(dynamicScheme.onPrimaryContainer),
            secondary = Color(dynamicScheme.secondary),
            secondaryDim = Color(dynamicScheme.secondaryFixedDim),
            onSecondary = Color(dynamicScheme.onSecondary),
            secondaryContainer = Color(dynamicScheme.secondaryContainer),
            onSecondaryContainer = Color(dynamicScheme.onSecondaryContainer),
            tertiary = Color(dynamicScheme.tertiary),
            tertiaryDim = Color(dynamicScheme.tertiaryFixedDim),
            onTertiary = Color(dynamicScheme.onTertiary),
            tertiaryContainer = Color(dynamicScheme.tertiaryContainer),
            onTertiaryContainer = Color(dynamicScheme.onTertiaryContainer),
            //background = Color(dynamicScheme.background),
            onBackground = Color(dynamicScheme.onBackground),
            onSurface = Color(dynamicScheme.onSurface),
            onSurfaceVariant = Color(dynamicScheme.onSurfaceVariant),
            surfaceContainerLow = Color(dynamicScheme.surfaceContainerLow),
            surfaceContainer = Color(dynamicScheme.surfaceContainer),
            surfaceContainerHigh = Color(dynamicScheme.surfaceContainerHigh),
            error = Color(dynamicScheme.error),
            onError = Color(dynamicScheme.onError)
        )
    } catch (e: Exception) {
        fallbackColor
    }
}