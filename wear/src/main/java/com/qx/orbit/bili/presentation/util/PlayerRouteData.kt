package com.qx.orbit.bili.presentation.util

import android.util.Base64
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.remote.GsonConfig
import java.net.URLDecoder

internal fun decodePlayerRouteData(raw: String): PlayerData? = runCatching {
    val json = when {
        raw.startsWith("{") -> raw
        raw.startsWith("%7B", ignoreCase = true) -> URLDecoder.decode(raw, "UTF-8")
        else -> String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    }
    GsonConfig.gson.fromJson(json, PlayerData::class.java)
}.getOrNull()
