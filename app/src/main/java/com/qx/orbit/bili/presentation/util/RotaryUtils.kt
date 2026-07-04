package com.qx.orbit.bili.presentation.util

import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults

object RotaryUtils {
    val isWearHapticsAvailable: Boolean by lazy {
        val model = Build.MODEL
        if (model == "M2505W1" || model == "M2501W1") {
            Log.d("WearHapticsUtil","XIAOMI WATCH 5 DETECTED, TURN OFF WEAR HAPTICS!")
            false
        } else {
            try {
                Class.forName("com.google.wear.input.WearHapticFeedbackConstants")
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

@Composable
fun rememberSafeRotaryScrollableBehavior(
    scrollableState: TransformingLazyColumnState,
    hapticFeedbackEnabled: Boolean = true
): RotaryScrollableBehavior {
    //Log.d("WearHapticsUtil","ENABLE WEAR HAPTICS:${RotaryUtils.isWearHapticsAvailable}")
    return RotaryScrollableDefaults.behavior(
        scrollableState = scrollableState,
        hapticFeedbackEnabled = hapticFeedbackEnabled && RotaryUtils.isWearHapticsAvailable
    )
}
