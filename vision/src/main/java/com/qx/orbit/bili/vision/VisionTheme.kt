package com.qx.orbit.bili.vision

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// M3 Expressive Dark Palette for Orbit Vision
val VisionDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF541D32),
    primaryContainer = Color(0xFF703348),
    onPrimaryContainer = Color(0xFFFFD8E4),
    inversePrimary = Color(0xFF8C4A60),
    secondary = Color(0xFFE5BDC8),
    onSecondary = Color(0xFF432932),
    secondaryContainer = Color(0xFF5C3F48),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = Color(0xFFF2B8B5),
    onTertiary = Color(0xFF4A2524),
    tertiaryContainer = Color(0xFF673B39),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFFFB1C8),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)

// M3 Expressive Light Palette fallback
val VisionLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C4A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8E4),
    onPrimaryContainer = Color(0xFF3B071D),
    secondary = Color(0xFF73565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7E5250),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF321110),
    background = Color(0xFFFCF8FA),
    onBackground = Color(0xFF1C1B1E),
    surface = Color(0xFFFCF8FA),
    onSurface = Color(0xFF1C1B1E),
    surfaceVariant = Color(0xFFE7E0E3),
    onSurfaceVariant = Color(0xFF494548),
    outline = Color(0xFF7A7579),
    outlineVariant = Color(0xFFCBC4C8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2F4),
    surfaceContainer = Color(0xFFF1ECF0),
    surfaceContainerHigh = Color(0xFFEBE6EA),
    surfaceContainerHighest = Color(0xFFE5E0E5)
)

val VisionShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VisionTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (VisionPreferences.themeMode) {
        VisionThemeMode.DARK -> true
        VisionThemeMode.LIGHT -> false
        VisionThemeMode.SYSTEM -> systemDark
    }

    val colors = when {
        VisionPreferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark ->
            dynamicDarkColorScheme(context)
        VisionPreferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        isDark -> VisionDarkColorScheme
        else -> VisionLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        shapes = VisionShapes,
        content = content
    )
}
