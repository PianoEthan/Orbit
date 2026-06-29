package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TimelineApi {

    private val api by lazy { BiliApiService.create() }

    internal data class TimelineData(
        @SerializedName("result") val result: List<TimelineItem>? = null
    )

    internal data class TimelineItem(
        @SerializedName("date") val date: String? = null,
        @SerializedName("date_ts") val date_ts: Long = 0,
        @SerializedName("day_of_week") val day_of_week: Int = 0,
        @SerializedName("episodes") val episodes: List<EpisodeItem>? = null,
        @SerializedName("is_today") val is_today: Int = 0
    )

    internal data class EpisodeItem(
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("delay") val delay: Int = 0,
        @SerializedName("episode_id") val episode_id: Long = 0,
        @SerializedName("pub_index") val pub_index: String? = null,
        @SerializedName("pub_time") val pub_time: String? = null,
        @SerializedName("pub_ts") val pub_ts: Long = 0,
        @SerializedName("published") val published: Int = 0,
        @SerializedName("follows") val follows: String? = null,
        @SerializedName("plays") val plays: String? = null,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("title") val title: String? = null
    )

    suspend fun getTimeline(types: String, before: Int, after: Int): List<Timeline.DayInfo> = withContext(Dispatchers.IO) {
        when (val resp = api.getTimeline(types, before, after)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<TimelineData>>() {}.type
                val apiResp: ApiResponse<TimelineData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data: TimelineData? = apiResp?.data
                data?.result?.map { item ->
                    Timeline.DayInfo(
                        date = item.date ?: "",
                        date_ts = item.date_ts,
                        day_of_week = item.day_of_week,
                        episodes = item.episodes?.map { ep ->
                            Timeline.Episode(
                                cover = ep.cover ?: "",
                                delay = ep.delay,
                                episode_id = ep.episode_id,
                                pub_index = ep.pub_index ?: "",
                                pub_time = ep.pub_time ?: "",
                                pub_ts = ep.pub_ts,
                                published = ep.published,
                                follows = ep.follows ?: "",
                                plays = ep.plays ?: "",
                                season_id = ep.season_id,
                                title = ep.title ?: ""
                            )
                        } ?: emptyList(),
                        is_today = item.is_today
                    )
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }
}
