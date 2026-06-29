package com.qx.orbit.bili.data.api
import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.util.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.model.VideoInfo
import com.qx.orbit.bili.data.model.Stats
import com.qx.orbit.bili.data.remote.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.qx.orbit.bili.data.model.Collection as BiliCollection

object VideoInfoApi {

    private val api by lazy { BiliApiService.create() }

    internal data class VideoInfoData(
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("desc") val desc: String? = null,
        @SerializedName("desc_v2") val desc_v2: List<DescV2>? = null,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("pubdate") val pubdate: Long = 0,
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("copyright") val copyright: Int = 0,
        @SerializedName("stat") val stat: StatData? = null,
        @SerializedName("pages") val pages: List<PageData>? = null,
        @SerializedName("is_upower_exclusive") val is_upower_exclusive: Boolean = false,
        @SerializedName("rights") val rights: RightsData? = null,
        @SerializedName("staff") val staff: List<StaffData>? = null,
        @SerializedName("owner") val owner: OwnerData? = null,
        @SerializedName("argue_info") val argue_info: ArgueInfoData? = null,
        @SerializedName("redirect_url") val redirect_url: String? = null,
        @SerializedName("ugc_season") val ugc_season: UgcSeasonData? = null,
        @SerializedName("req_user") val req_user: ReqUserData? = null
    )

    internal data class ReqUserData(
        @SerializedName("attention") val attention: Int = 0,
        @SerializedName("favorite") val favorite: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("dislike") val dislike: Int = 0,
        @SerializedName("coin") val coin: Int = 0
    )

    internal data class DescV2(@SerializedName("type") val type: Int = 0, @SerializedName("raw_text") val raw_text: String? = null, @SerializedName("biz_id") val biz_id: Long = 0)
    internal data class StatData(@SerializedName("view") val view: Int = 0, @SerializedName("like") val like: Int = 0, @SerializedName("coin") val coin: Int = 0, @SerializedName("reply") val reply: Int = 0, @SerializedName("danmaku") val danmaku: Int = 0, @SerializedName("favorite") val favorite: Int = 0)
    internal data class PageData(@SerializedName("part") val part: String? = null, @SerializedName("cid") val cid: Long = 0)
    internal data class RightsData(@SerializedName("is_cooperation") val is_cooperation: Int = 0, @SerializedName("is_stein_gate") val is_stein_gate: Int = 0, @SerializedName("is_360") val is_360: Int = 0)
    internal data class OfficialData(@SerializedName("role") val role: Int = -1, @SerializedName("title") val title: String? = null, @SerializedName("desc") val desc: String? = null, @SerializedName("type") val type: Int = -1)
    internal data class VipData(@SerializedName("type") val type: Int = 0, @SerializedName("vipType") val vipType: Int = 0, @SerializedName("status") val status: Int = 0, @SerializedName("vipStatus") val vipStatus: Int = 0)
    internal data class StaffData(@SerializedName("mid") val mid: Long = 0, @SerializedName("title") val title: String? = null, @SerializedName("name") val name: String? = null, @SerializedName("face") val face: String? = null, @SerializedName("follower") val follower: Int = 0, @SerializedName("official") val official: OfficialData? = null, @SerializedName("vip") val vip: VipData? = null, @SerializedName("official_verify") val official_verify: OfficialData? = null)
    internal data class OwnerData(@SerializedName("name") val name: String? = null, @SerializedName("face") val face: String? = null, @SerializedName("mid") val mid: Long = 0, @SerializedName("vip") val vip: VipData? = null, @SerializedName("official_verify") val official_verify: OfficialData? = null)
    internal data class ArgueInfoData(@SerializedName("argue_msg") val argue_msg: String? = null)
    internal data class UgcSeasonData(@SerializedName("id") val id: Int = 0, @SerializedName("title") val title: String? = null, @SerializedName("intro") val intro: String? = null, @SerializedName("cover") val cover: String? = null, @SerializedName("mid") val mid: Long = 0, @SerializedName("stat") val stat: UgcStatData? = null, @SerializedName("sections") val sections: List<UgcSectionData>? = null)
    internal data class UgcStatData(@SerializedName("view") val view: Long = 0)
    internal data class UgcSectionData(@SerializedName("season_id") val season_id: Int = 0, @SerializedName("id") val id: Int = 0, @SerializedName("title") val title: String? = null, @SerializedName("episodes") val episodes: List<UgcEpisodeData>? = null)
    internal data class UgcEpisodeData(@SerializedName("season_id") val season_id: Int = 0, @SerializedName("section_id") val section_id: Int = 0, @SerializedName("id") val id: Int = 0, @SerializedName("aid") val aid: Long = 0, @SerializedName("cid") val cid: Long = 0, @SerializedName("title") val title: String? = null, @SerializedName("arc") val arc: VideoInfoData? = null, @SerializedName("bvid") val bvid: String? = null)
    internal data class TagData(@SerializedName("tag_name") val tag_name: String? = null)
    internal data class TotalData(@SerializedName("total") val total: Any? = null)
    internal data class RelationData(
        @SerializedName("attention") val attention: Int = 0,
        @SerializedName("favorite") val favorite: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("dislike") val dislike: Int = 0,
        @SerializedName("coin") val coin: Int = 0
    )

    suspend fun getVideoInfo(bvid: String): VideoInfo? = withContext(Dispatchers.IO) {
        fetchAndBuildVideoInfo(bvid = bvid, aid = null)
    }

    suspend fun getVideoInfo(aid: Long): VideoInfo? = withContext(Dispatchers.IO) {
        fetchAndBuildVideoInfo(bvid = null, aid = aid)
    }

    private suspend fun fetchAndBuildVideoInfo(bvid: String?, aid: Long?): VideoInfo? {
        val result = api.getVideoInfo(bvid = bvid, aid = aid)
        val jsonElement = when (result) {
            is Result.Success -> result.data
            is Result.Error -> return null
        }
        val type = object : TypeToken<ApiResponse<VideoInfoData>>() {}.type
        val resp: ApiResponse<VideoInfoData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return null
        var videoInfo = buildVideoInfo(resp.data, fetchDetailedUser = true)

        try {
            if (CookieManager.getCookie().isNotEmpty()) {
                when (val statsResult = api.getVideoStats(videoInfo.aid)) {
                    is Result.Success -> {
                        val type = object : TypeToken<ApiResponse<RelationData>>() {}.type
                        val apiResp: ApiResponse<RelationData>? = GsonConfig.gson.fromJson(statsResult.data, type)
                        val rel = apiResp?.data
                        if (rel != null) {
                            val updatedStats = videoInfo.stats?.copy(
                                followed = rel.attention == 1,
                                liked = rel.like == 1,
                                disliked = rel.dislike == 1,
                                favoured = rel.favorite == 1,
                                coined = rel.coin
                            )
                            videoInfo = videoInfo.copy(stats = updatedStats)
                        }
                    }
                    is Result.Error -> {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videoInfo
    }

    suspend fun getTags(bvid: String): String = withContext(Dispatchers.IO) {
        fetchTags(bvid = bvid, aid = null)
    }

    suspend fun getTags(aid: Long): String = withContext(Dispatchers.IO) {
        fetchTags(bvid = null, aid = aid)
    }

    private suspend fun fetchTags(bvid: String?, aid: Long?): String {
        val result = api.getVideoTags(bvid = bvid, aid = aid)
        val jsonElement = when (result) {
            is Result.Success -> result.data
            is Result.Error -> return ""
        }
        if (!jsonElement.isJsonArray) return ""
        return jsonElement.asJsonArray.mapNotNull { element ->
            GsonConfig.gson.fromJson(element, TagData::class.java)
        }.joinToString("/") { it.tag_name ?: "" }
    }

    suspend fun getWatching(aid: Long, cid: Long): String = withContext(Dispatchers.IO) {
        val result = api.getWatching(aid = aid, cid = cid)
        val jsonElement = when (result) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext ""
        }
        val type = object : TypeToken<ApiResponse<TotalData>>() {}.type
        val resp: ApiResponse<TotalData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || resp.data == null || resp.data.total == null) return@withContext ""
        val total = resp.data.total
        if (total is String) total
        else StringUtil.toWan((total as Number).toLong())
    }

    internal suspend fun analyzeUgcSeason(data: UgcSeasonData): BiliCollection {
        return BiliCollection(
            id = data.id,
            title = data.title ?: "",
            intro = data.intro ?: "",
            cover = data.cover ?: "",
            mid = data.mid,
            view = StringUtil.toWan(data.stat?.view ?: 0),
            sections = data.sections?.filterNotNull()?.map { sectionData ->
                BiliCollection.Section(
                    season_id = sectionData.season_id,
                    id = sectionData.id,
                    title = sectionData.title ?: "",
                    episodes = sectionData.episodes?.filterNotNull()?.map { epData ->
                        BiliCollection.Episode(
                            season_id = epData.season_id,
                            section_id = epData.section_id,
                            id = epData.id.toLong(),
                            aid = epData.aid,
                            cid = epData.cid,
                            title = epData.title ?: "",
                            bvid = epData.bvid ?: "",
                            arc = epData.arc?.let { buildVideoInfo(it, fetchDetailedUser = false) }
                        )
                    } ?: emptyList()
                )
            } ?: emptyList()
        )
    }

    private suspend fun buildVideoInfo(data: VideoInfoData, fetchDetailedUser: Boolean): VideoInfo {
        val description: String
        val descAts: List<At>

        if (!data.desc_v2.isNullOrEmpty()) {
            val sb = StringBuilder()
            val ats = mutableListOf<At>()
            for (desc in data.desc_v2) {
                if (desc.type == 2) {
                    val start = sb.length
                    sb.append("@").append(desc.raw_text ?: "")
                    val end = sb.length
                    ats.add(At(desc.biz_id, start, end))
                } else {
                    sb.append(desc.raw_text ?: "")
                }
            }
            description = sb.toString()
            descAts = ats
        } else {
            description = data.desc ?: ""
            descAts = emptyList()
        }

        val timeDesc = if (data.pubdate > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(data.pubdate * 1000)
        } else ""

        val stats = data.stat?.let {
            Stats(
                view = it.view, like = it.like, coin = it.coin,
                reply = it.reply, danmaku = it.danmaku, favorite = it.favorite,
                coin_limit = if (data.copyright == VideoInfo.COPYRIGHT_REPRINT) 1 else 2,
                liked = data.req_user?.like == 1,
                coined = data.req_user?.coin ?: 0,
                favoured = data.req_user?.favorite == 1,
                disliked = data.req_user?.dislike == 1,
                followed = data.req_user?.attention == 1
            )
        }

        val pagenames = data.pages?.filterNotNull()?.map { it.part ?: "" } ?: emptyList()
        val cids = data.pages?.filterNotNull()?.map { it.cid } ?: emptyList()

        val isCooperation = data.rights?.is_cooperation == 1
        val isSteinGate = data.rights?.is_stein_gate == 1
        val is360 = data.rights?.is_360 == 1

        val staff = if (isCooperation && !data.staff.isNullOrEmpty()) {
            val list = data.staff.filterNotNull().map { s ->
                val officialRole = if (s.official?.role != null && s.official.role != -1) s.official.role else {
                    when (s.official_verify?.type) {
                        0 -> 1
                        1 -> 2
                        else -> 0
                    }
                }
                val vipStatus = if (s.vip?.status != null && s.vip.status != 0) s.vip.status else s.vip?.vipStatus ?: 0
                UserInfo(
                    mid = s.mid, sign = s.title ?: "", name = s.name ?: "",
                    avatar = s.face ?: "", fans = s.follower, level = 6,
                    official = officialRole, officialDesc = s.official?.title ?: s.official?.desc ?: "",
                    vip_role = vipStatus
                )
            }
            if (fetchDetailedUser) {
                kotlinx.coroutines.coroutineScope {
                    list.map { s ->
                        async {
                            try {
                                val full = UserInfoApi.getUserInfo(s.mid)
                                s.copy(
                                    vip_role = full?.vip_role ?: s.vip_role,
                                    official = full?.official ?: s.official,
                                    officialDesc = full?.officialDesc ?: s.officialDesc
                                )
                            } catch (e: Exception) { s }
                        }
                    }.awaitAll()
                }
            } else list
        } else if (data.owner != null) {
            val vipStatus = if (data.owner.vip?.status != null && data.owner.vip.status != 0) data.owner.vip.status else data.owner.vip?.vipStatus ?: 0
            val officialRole = when (data.owner.official_verify?.type) {
                0 -> 1
                1 -> 2
                else -> data.owner.official_verify?.role ?: 0
            }
            val officialDesc = data.owner.official_verify?.title ?: data.owner.official_verify?.desc ?: ""
            val ownerInfo = listOf(UserInfo(name = data.owner.name ?: "", avatar = data.owner.face ?: "", mid = data.owner.mid, sign = "UP主", vip_role = vipStatus, official = officialRole, officialDesc = officialDesc))
            
            if (fetchDetailedUser) {
                try {
                    val full = UserInfoApi.getUserInfo(data.owner.mid)
                    listOf(ownerInfo[0].copy(
                        vip_role = full?.vip_role ?: ownerInfo[0].vip_role,
                        official = full?.official ?: ownerInfo[0].official,
                        officialDesc = full?.officialDesc ?: ownerInfo[0].officialDesc
                    ))
                } catch (e: Exception) { ownerInfo }
            } else ownerInfo
        } else emptyList()

        val epid = try {
            if (!data.redirect_url.isNullOrEmpty() && data.redirect_url.contains("bangumi")) {
                data.redirect_url.replace("https://www.bilibili.com/bangumi/play/ep", "").toLong()
            } else -1
        } catch (_: Exception) { -1 }

        val collection = data.ugc_season?.let { analyzeUgcSeason(it) }

        return VideoInfo(
            title = data.title ?: "", cover = data.pic ?: "", bvid = data.bvid ?: "",
            aid = data.aid, description = description, descAts = descAts,
            duration = StringUtil.toTime(data.duration), stats = stats, timeDesc = timeDesc,
            copyright = data.copyright, pagenames = pagenames, cids = cids,
            upowerExclusive = data.is_upower_exclusive, argueMsg = data.argue_info?.argue_msg,
            isCooperation = isCooperation, isSteinGate = isSteinGate, is360 = is360,
            staff = staff, epid = epid, collection = collection
        )
    }

}
