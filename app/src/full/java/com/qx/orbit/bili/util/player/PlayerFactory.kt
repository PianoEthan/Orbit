package com.qx.orbit.bili.util.player

import android.content.Context
import com.qx.orbit.bili.util.SharedPreferencesUtil
import com.qx.orbit.bili.util.player.ijk.IjkPlayerWrapper
import com.qx.orbit.bili.util.player.media3.Media3PlayerWrapper

fun createOrbitPlayer(context: Context, isInteractive: Boolean = false): OrbitPlayer =
    if (isInteractive) {
        Media3PlayerWrapper(context)
    } else {
        when (SharedPreferencesUtil.getString("player_engine", "ijk")) {
            "media3" -> Media3PlayerWrapper(context)
            else -> IjkPlayerWrapper()
        }
    }
