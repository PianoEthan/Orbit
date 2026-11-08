package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName

data class Bangumi(
    val info: Info? = null,
    val sectionList: List<Section> = emptyList()
) {
    data class Info(
        val media_id: Long = 0,
        val season_id: Long = 0,
        val type: Int = 0,
        val count: Int = 0,
        val score: Float = 0f,
        val title: String = "",
        val cover: String = "",
        @SerializedName("horizontal_picture") val cover_horizontal: String = "",
        val type_name: String = "",
        val area_name: String = "",
        @SerializedName("index_show") val indexShow: String = "",
        val evaluate: String = "",
        val staff: String = "",
        val record: String = "",
        val subtitle: String = "",
        val publish: Publish? = null,
        val styles: List<String> = emptyList(),
        val stat: Stat? = null,
        val up_info: UpInfo? = null,
        val series: Series? = null,
        val seasons: List<Season> = emptyList(),
        val user_status: UserStatus? = null
    )

    data class UserStatus(
        val progress: Progress? = null,
        val follow: Int = 0
    )

    data class Progress(
        val last_ep_id: Long = 0,
        val last_ep_index: String = "",
        val last_time: Long = 0
    )

    data class Publish(
        val is_finish: Int = 0,
        val is_started: Int = 0,
        val pub_time: String = "",
        val pub_time_show: String = ""
    )

    data class Stat(
        val favorites: Int = 0,
        val series_follow: Int = 0,
        val views: Int = 0,
        val vt: Int = 0
    )

    data class UpInfo(
        val mid: Long = 0,
        val name: String = "",
        val avatar: String = ""
    )

    data class Series(
        val series_id: Long = 0,
        val series_title: String = ""
    )

    data class Season(
        val media_id: Long = 0,
        val season_id: Long = 0,
        val season_title: String = "",
        val cover: String = "",
        val badge: String = ""
    )

    data class Section(
        val id: Long = 0,
        val type: Int = 0,
        val title: String = "",
        val episodes: List<Episode> = emptyList()
    )

    data class Episode(
        val id: Long = 0,
        val aid: Long = 0,
        val cid: Long = 0,
        val title: String = "",
        @SerializedName("long_title") val title_long: String = "",
        val cover: String = "",
        val badge: String = ""
    )
}

data class Timeline(
    val dayList: List<DayInfo> = emptyList()
) {
    data class DayInfo(
        val date: String = "",
        val date_ts: Long = 0,
        val day_of_week: Int = 0,
        val episodes: List<Episode> = emptyList(),
        val is_today: Int = 0
    )

    data class Episode(
        val cover: String = "",
        val delay: Int = 0,
        val episode_id: Long = 0,
        val pub_index: String = "",
        val pub_time: String = "",
        val pub_ts: Long = 0,
        val published: Int = 0,
        val follows: String = "",
        val plays: String = "",
        val season_id: Long = 0,
        val title: String = ""
    )
}
