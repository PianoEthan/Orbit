package com.qx.orbit.bili.presentation.util

internal fun wearSwipeEnabled(
    systemBackOnly: Boolean,
    isMiWatch5: Boolean,
    isPlayer: Boolean,
    isAudioPlayer: Boolean
): Boolean = !systemBackOnly && !isMiWatch5 && (!isPlayer || isAudioPlayer)
