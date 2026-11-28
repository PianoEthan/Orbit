package com.qx.orbit.bili.core.api

data class CoreSeason(val id: Long, val mediaId: Long, val title: String, val cover: String, val subtitle: String, val badge: String)
data class CoreSeasonPage(val items: List<CoreSeason>, val hasMore: Boolean)
data class CoreSeasonEpisode(val id: Long, val title: String, val badge: String, val video: CoreVideo)
data class CoreSeasonDetail(val season: CoreSeason, val description: String, val episodes: List<CoreSeasonEpisode>, val seasons: List<CoreSeason>,
    val followed: Boolean, val lastEpisodeId: Long, val lastPositionMs: Long)

interface SeasonGateway {
    suspend fun loadCatalog(type: Int, page: Int, cookie: String): CoreSeasonPage
    suspend fun loadFollowed(mid: Long, type: Int, page: Int, cookie: String): CoreSeasonPage
    suspend fun loadSeason(seasonId: Long, cookie: String): CoreSeasonDetail
    suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String)
}
