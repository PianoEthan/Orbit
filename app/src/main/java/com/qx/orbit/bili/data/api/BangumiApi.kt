package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object BangumiApi {

    private val api by lazy { BiliApiService.create() }

    internal data class BangumiFollowData(
        @SerializedName("list") val list: List<BangumiFollowItem>? = null,
        @SerializedName("total") val total: Int = 0
    )

    internal data class BangumiFollowItem(
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("season_type") val season_type: Int = 0,
        @SerializedName("total_count") val total_count: Int = 0,
        @SerializedName("is_finish") val is_finish: Int = 0,
        @SerializedName("new_ep") val new_ep: NewEpData? = null,
        @SerializedName("rating") val rating: RatingData? = null,
        @SerializedName("badge") val badge: String? = null,
        @SerializedName("badge_type") val badge_type: Int = 0,
        @SerializedName("follows") val follows: String? = null,
        @SerializedName("series") val series: SeriesData? = null,
        @SerializedName("areas") val areas: List<AreaData>? = null,
        @SerializedName("publish") val publish: PublishData? = null,
        @SerializedName("season_title") val season_title: String? = null,
        @SerializedName("stat") val stat: SeasonStat? = null,
        @SerializedName("url") val url: String? = null
    )

    internal data class FollowStat(
        @SerializedName("view") val view: Int = 0,
        @SerializedName("danmaku") val danmaku: Int = 0,
        @SerializedName("reply") val reply: Int = 0,
        @SerializedName("favorite") val favorite: Int = 0,
        @SerializedName("coin") val coin: Int = 0,
        @SerializedName("share") val share: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("vt") val vt: Int = 0
    )

    internal data class ReviewData(
        @SerializedName("media") val media: MediaData? = null,
        @SerializedName("review") val review: ReviewResult? = null
    )

    internal data class ReviewResult(
        @SerializedName("score") val score: Float = 0f
    )

    internal data class MediaData(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("horizontal_picture") val horizontal_picture: String? = null,
        @SerializedName("new_ep") val new_ep: NewEpData? = null,
        @SerializedName("rating") val rating: RatingData? = null,
        @SerializedName("areas") val areas: List<AreaData>? = null,
        @SerializedName("publish") val publish: PublishData? = null,
        @SerializedName("stat") val stat: SeasonStat? = null,
        @SerializedName("up_info") val up_info: UpInfoData? = null
    )

    internal data class NewEpData(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("index") val index: String? = null,
        @SerializedName("index_show") val index_show: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("pub_time") val pub_time: String? = null
    )

    internal data class RatingData(
        @SerializedName("score") val score: Float = 0f,
        @SerializedName("count") val count: Int = 0
    )

    internal data class AreaData(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("name") val name: String? = null
    )

    internal data class SeasonDetailData(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("horizontal_picture") val horizontal_picture: String? = null,
        @SerializedName("new_ep") val new_ep: NewEpData? = null,
        @SerializedName("rating") val rating: RatingData? = null,
        @SerializedName("areas") val areas: List<AreaData>? = null,
        @SerializedName("publish") val publish: PublishData? = null,
        @SerializedName("stat") val stat: SeasonStat? = null,
        @SerializedName("up_info") val up_info: UpInfoData? = null,
        @SerializedName("season_title") val season_title: String? = null,
        @SerializedName("evaluate") val evaluate: String? = null,
        @SerializedName("type_name") val type_name: String? = null,
        @SerializedName("series") val series: SeriesData? = null,
        @SerializedName("seasons") val seasons: List<SeasonItem>? = null,
        @SerializedName("total_ep") val total_ep: Int = 0,
        @SerializedName("staff") val staff: String? = null
    )

    internal data class SeasonResult(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("horizontal_picture") val horizontal_picture: String? = null,
        @SerializedName("new_ep") val new_ep: NewEpData? = null,
        @SerializedName("rating") val rating: RatingData? = null,
        @SerializedName("areas") val areas: List<AreaData>? = null,
        @SerializedName("publish") val publish: PublishData? = null,
        @SerializedName("stat") val stat: SeasonStat? = null,
        @SerializedName("up_info") val up_info: UpInfoData? = null,
        @SerializedName("evaluate") val evaluate: String? = null,
        @SerializedName("type_name") val type_name: String? = null,
        @SerializedName("series") val series: SeriesData? = null,
        @SerializedName("seasons") val seasons: List<SeasonItem>? = null,
        @SerializedName("total_ep") val total_ep: Int = 0,
        @SerializedName("staff") val staff: String? = null
    )

    internal data class PublishData(
        @SerializedName("is_finish") val is_finish: Int = 0,
        @SerializedName("is_started") val is_started: Int = 0,
        @SerializedName("pub_time") val pub_time: String? = null,
        @SerializedName("pub_time_show") val pub_time_show: String? = null
    )

    internal data class SeasonStat(
        @SerializedName("favorites") val favorites: Int = 0,
        @SerializedName("series_follow") val series_follow: Int = 0,
        @SerializedName("views") val views: Int = 0,
        @SerializedName("vt") val vt: Int = 0,
        @SerializedName("danmakus") val danmakus: Int = 0,
        @SerializedName("reply") val reply: Int = 0
    )

    internal data class UpInfoData(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("avatar") val avatar: String? = null
    )

    internal data class SeriesData(
        @SerializedName("series_id") val series_id: Long = 0,
        @SerializedName("series_title") val series_title: String? = null
    )

    internal data class SeasonItem(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("season_title") val season_title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("badge") val badge: String? = null
    )

    internal data class MainSectionData(
        @SerializedName("episodes") val episodes: List<EpisodeData>? = null
    )

    internal data class SectionData(
        @SerializedName("main_section") val main_section: MainSectionData? = null,
        @SerializedName("section") val section: List<SectionResult>? = null
    )

    internal data class SectionResult(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("episodes") val episodes: List<EpisodeData>? = null
    )

    internal data class SectionItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("episodes") val episodes: List<EpisodeData>? = null
    )

    internal data class EpisodeData(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("long_title") val title_long: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("badge") val badge: String? = null
    )

    internal data class SeasonIdData(
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("horizontal_picture") val horizontal_picture: String? = null,
        @SerializedName("new_ep") val new_ep: NewEpData? = null,
        @SerializedName("rating") val rating: RatingData? = null,
        @SerializedName("areas") val areas: List<AreaData>? = null,
        @SerializedName("publish") val publish: PublishData? = null,
        @SerializedName("stat") val stat: SeasonStat? = null,
        @SerializedName("up_info") val up_info: UpInfoData? = null,
        @SerializedName("evaluate") val evaluate: String? = null,
        @SerializedName("type_name") val type_name: String? = null,
        @SerializedName("series") val series: SeriesData? = null,
        @SerializedName("seasons") val seasons: List<SeasonItem>? = null,
        @SerializedName("total_ep") val total_ep: Int = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("staff") val staff: String? = null
    )

    internal data class SeasonIdResult(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("evaluate") val evaluate: String? = null,
        @SerializedName("type_name") val type_name: String? = null
    )

    suspend fun getFollowingList(mid: Long, page: Int): Pair<Int, List<VideoCard>> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/space/bangumi/follow/list?vmid=$mid&type=1&pn=$page&ps=15"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<BangumiFollowData>>() {}.type
        val resp: ApiResponse<BangumiFollowData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess) return@withContext Pair(-1, emptyList<VideoCard>())
        val data = resp.data ?: return@withContext Pair(1, emptyList<VideoCard>())
        val list = data.list
        if (list.isNullOrEmpty()) return@withContext Pair(1, emptyList<VideoCard>())
        val cards = list.map { item ->
            VideoCard(
                title = item.title ?: "",
                upName = "",
                view = item.stat?.views?.let { StringUtil.toWan(it.toLong()) } ?: "",
                cover = item.cover ?: "",
                type = "bangumi",
                aid = item.media_id,
                bvid = "",
                cid = 0
            )
        }
        Pair(0, cards)
    }

    suspend fun getBangumi(mediaId: Long): Bangumi? = withContext(Dispatchers.IO) {
        val info = getInfo(mediaId) ?: return@withContext null
        val sectionList = if (info.season_id > 0) getSections(info.season_id) else emptyList()
        Bangumi(info = info, sectionList = sectionList)
    }

    suspend fun getMdidFromEpid(epid: Long): Long? = withContext(Dispatchers.IO) {
        when (val resp = api.getSeasonInfo(epId = epid)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<SeasonIdData>>() {}.type
                val parsed: ApiResponse<SeasonIdData>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data?.media_id?.takeIf { it > 0 }
            }
            is Result.Error -> null
        }
    }

    suspend fun getInfo(id: Long): Bangumi.Info? = withContext(Dispatchers.IO) {
        // Assume id is season_id first, which is what SearchApi will pass.
        // If the ID was actually media_id, we can try to get review first to find season_id.
        var seasonId = id
        var mediaId = 0L
        var score = 0f
        
        // Try as season_id
        var seasonJson = when (val r = api.getSeasonInfo(seasonId = seasonId)) {
            is Result.Success -> r.data
            is Result.Error -> null
        }
        var seasonType = object : TypeToken<ApiResponse<SeasonDetailData>>() {}.type
        var seasonResp: ApiResponse<SeasonDetailData>? = GsonConfig.gson.fromJson(seasonJson, seasonType)
        
        // If it failed, maybe the id is media_id?
        if (seasonResp == null || !seasonResp.isSuccess || seasonResp.data == null) {
            mediaId = id
            val reviewJson = when (val r = api.getBangumiReview(mediaId)) {
                is Result.Success -> r.data
                is Result.Error -> null
            }
            val reviewType = object : TypeToken<ApiResponse<ReviewData>>() {}.type
            val reviewResp: ApiResponse<ReviewData>? = GsonConfig.gson.fromJson(reviewJson, reviewType)
            if (reviewResp != null && reviewResp.isSuccess && reviewResp.data?.media != null) {
                seasonId = reviewResp.data.media.season_id
                score = reviewResp.data.review?.score ?: reviewResp.data.media.rating?.score ?: 0f
                if (seasonId > 0) {
                    seasonJson = when (val r = api.getSeasonInfo(seasonId = seasonId)) {
                        is Result.Success -> r.data
                        is Result.Error -> null
                    }
                    seasonResp = GsonConfig.gson.fromJson(seasonJson, seasonType)
                } else {
                    // It doesn't have a season_id (e.g. only media info exists)
                    val media = reviewResp.data.media
                    return@withContext Bangumi.Info(
                        media_id = media.media_id,
                        season_id = 0,
                        type = media.type,
                        count = media.new_ep?.index_show?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0,
                        score = score,
                        title = media.title ?: "",
                        cover = media.horizontal_picture ?: "", // fallback
                        cover_horizontal = media.horizontal_picture ?: "",
                        type_name = "",
                        area_name = media.areas?.firstOrNull()?.name ?: "",
                        indexShow = media.new_ep?.index_show ?: "",
                        evaluate = "",
                        staff = "",
                        record = "",
                        subtitle = "",
                        publish = media.publish?.let {
                            Bangumi.Publish(
                                is_finish = it.is_finish,
                                is_started = it.is_started,
                                pub_time = it.pub_time ?: "",
                                pub_time_show = it.pub_time_show ?: ""
                            )
                        },
                        styles = emptyList(),
                        stat = media.stat?.let {
                            Bangumi.Stat(
                                favorites = it.favorites,
                                series_follow = it.series_follow,
                                views = it.views,
                                vt = it.vt
                            )
                        },
                        up_info = media.up_info?.let {
                            Bangumi.UpInfo(mid = it.mid, name = it.name ?: "", avatar = it.avatar ?: "")
                        },
                        series = null,
                        seasons = emptyList()
                    )
                }
            } else {
                return@withContext null
            }
        }
        
        val result = seasonResp?.data ?: return@withContext null
        mediaId = result.media_id
        
        // Try to fetch review score if we haven't already
        if (score == 0f && mediaId > 0) {
            val reviewJson = when (val r = api.getBangumiReview(mediaId)) {
                is Result.Success -> r.data
                is Result.Error -> null
            }
            val reviewType = object : TypeToken<ApiResponse<ReviewData>>() {}.type
            val reviewResp: ApiResponse<ReviewData>? = GsonConfig.gson.fromJson(reviewJson, reviewType)
            if (reviewResp != null && reviewResp.isSuccess && reviewResp.data != null) {
                score = reviewResp.data.review?.score ?: reviewResp.data.media?.rating?.score ?: result.rating?.score ?: 0f
            } else {
                score = result.rating?.score ?: 0f
            }
        } else if (score == 0f) {
            score = result.rating?.score ?: 0f
        }

        Bangumi.Info(
            media_id = result.media_id,
            season_id = result.season_id,
            type = result.type,
            count = result.total_ep,
            score = score,
            title = result.title ?: "",
            cover = result.cover ?: "",
            cover_horizontal = result.horizontal_picture ?: "",
            type_name = result.type_name ?: "",
            area_name = result.areas?.firstOrNull()?.name ?: "",
            indexShow = result.new_ep?.index_show ?: "",
            evaluate = result.evaluate ?: "",
            staff = result.staff ?: "",
            record = "",
            subtitle = "",
            publish = result.publish?.let {
                Bangumi.Publish(
                    is_finish = it.is_finish,
                    is_started = it.is_started,
                    pub_time = it.pub_time ?: "",
                    pub_time_show = it.pub_time_show ?: ""
                )
            },
            styles = emptyList(),
            stat = result.stat?.let {
                Bangumi.Stat(
                    favorites = it.favorites,
                    series_follow = it.series_follow,
                    views = it.views,
                    vt = it.vt
                )
            },
            up_info = result.up_info?.let {
                Bangumi.UpInfo(mid = it.mid, name = it.name ?: "", avatar = it.avatar ?: "")
            },
            series = result.series?.let {
                Bangumi.Series(series_id = it.series_id, series_title = it.series_title ?: "")
            },
            seasons = result.seasons?.map { s ->
                Bangumi.Season(
                    media_id = s.media_id,
                    season_id = s.season_id,
                    season_title = s.season_title ?: "",
                    cover = s.cover ?: "",
                    badge = s.badge ?: ""
                )
            } ?: emptyList()
        )
    }

    suspend fun getSections(seasonId: Long): List<Bangumi.Section> = withContext(Dispatchers.IO) {
        val json = when (val r = api.getSeasonSection(seasonId)) {
            is Result.Success -> r.data
            is Result.Error -> return@withContext emptyList()
        }
        val type = object : TypeToken<ApiResponse<SectionData>>() {}.type
        val resp: ApiResponse<SectionData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        val sections = mutableListOf<Bangumi.Section>()
        
        resp.data.main_section?.episodes?.let { eps ->
            if (eps.isNotEmpty()) {
                sections.add(
                    Bangumi.Section(
                        id = 0,
                        type = 0,
                        title = "正片",
                        episodes = eps.map { ep ->
                            Bangumi.Episode(
                                id = ep.id,
                                aid = ep.aid,
                                cid = ep.cid,
                                title = ep.title ?: "",
                                title_long = ep.title_long ?: "",
                                cover = ep.cover ?: "",
                                badge = ep.badge ?: ""
                            )
                        }
                    )
                )
            }
        }
        
        resp.data.section?.forEach { section ->
            sections.add(
                Bangumi.Section(
                    id = section.id,
                    type = section.type,
                    title = section.title ?: "",
                    episodes = section.episodes?.map { ep ->
                        Bangumi.Episode(
                            id = ep.id,
                            aid = ep.aid,
                            cid = ep.cid,
                            title = ep.title ?: "",
                            title_long = ep.title_long ?: "",
                            cover = ep.cover ?: "",
                            badge = ep.badge ?: ""
                        )
                    } ?: emptyList()
                )
            )
        }
        
        sections
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
