package com.qx.orbit.bili.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.qx.orbit.bili.presentation.theme.LocalScreenRound

@Composable
fun Modifier.adaptiveTransformedHeight(
    scope: TransformingLazyColumnItemScope,
    transformationSpec: TransformationSpec
): Modifier {
    val isRound = LocalScreenRound.current
    return if (isRound) {
        this.transformedHeight(scope, transformationSpec)
    } else {
        this
    }
}

@Composable
fun TransformingLazyColumnItemScope.rememberAdaptiveSurfaceTransformation(
    transformationSpec: TransformationSpec
): SurfaceTransformation? {
    val isRound = LocalScreenRound.current
    val scope = this
    return remember(scope, transformationSpec, isRound) {
        if (isRound) with(scope) { SurfaceTransformation(transformationSpec) } else null
    }
}
