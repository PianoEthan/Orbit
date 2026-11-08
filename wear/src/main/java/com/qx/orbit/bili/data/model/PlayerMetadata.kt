package com.qx.orbit.bili.data.model

import com.google.gson.JsonElement

data class PlayerMetadata(
    val interaction: PlayerInteraction? = null,
    val subtitleLinks: List<SubtitleLink> = emptyList(),
    val viewPoints: List<ViewPoint> = emptyList(),
    val history: History? = null
)

data class PlayerInteraction(
    val graphVersion: Long,
    val message: String = "",
    val errorToast: String = "",
    val mark: Int = 0,
    val needReload: Int? = null,
    val historyNode: JsonElement? = null
)
