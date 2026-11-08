package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName

data class FavoriteFolder(
    @SerializedName(value = "fav_box", alternate = ["id", "fid"]) val id: Long = 0,
    @SerializedName(value = "id", alternate = ["media_id"]) val mediaId: Long = 0,
    val name: String = "",
    val cover: String = "",
    @SerializedName("count") val videoCount: Int = 0,
    @SerializedName("max_count") val maxCount: Int = 0,
    val isDefault: Boolean = false,
    val intro: String = "",
    val isPrivate: Boolean = false
)

data class Collection(
    val id: Int = 0,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val mid: Long = 0,
    val sections: List<Section> = emptyList(),
    val cards: List<VideoCard> = emptyList(),
    val view: String = ""
) {
    data class Section(
        val season_id: Int = 0,
        val id: Int = 0,
        val title: String = "",
        val type: Int = 0,
        val episodes: List<Episode> = emptyList()
    )

    data class Episode(
        val season_id: Int = 0,
        val section_id: Int = 0,
        val id: Long = 0,
        val aid: Long = 0,
        val cid: Long = 0,
        val title: String = "",
        val bvid: String = "",
        val arc: VideoInfo? = null
    )
}

data class Series(
    val type: String = "series",
    val id: Int = 0,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val mid: Long = 0,
    val total: String = ""
)
