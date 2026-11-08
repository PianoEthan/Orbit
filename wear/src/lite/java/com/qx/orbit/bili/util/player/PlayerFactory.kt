package com.qx.orbit.bili.util.player

import android.content.Context
import com.qx.orbit.bili.util.player.media3.Media3PlayerWrapper

fun createOrbitPlayer(context: Context, isInteractive: Boolean = false): OrbitPlayer =
    Media3PlayerWrapper(context)
