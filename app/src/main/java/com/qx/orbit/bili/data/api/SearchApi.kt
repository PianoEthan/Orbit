package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object SearchApi {

    private val api by lazy { BiliApiService.create() }

    internal data class SearchVideoItem(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("author") val author: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("play") val play: Int = 0,
        @SerializedName("duration") val duration: String? = null,
        @SerializedName("cid") val cid: Long = 0
    )

    internal data class OfficialData(@SerializedName("type") val type: Int = -1, @SerializedName("desc") val desc: String? = null)
    internal data class VipData(@SerializedName("vipType") val vipType: Int = 0, @SerializedName("type") val type: Int = 0)

    internal data class SearchUserItem(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("usign") val usign: String? = null,
        @SerializedName("fans") val fans: Int = 0,
        @SerializedName("level") val level: Int = 0,
        @SerializedName("upic") val upic: String? = null,
        @SerializedName("official_verify") val official_verify: OfficialData? = null,
        @SerializedName("vip") val vip: VipData? = null
    )

    internal data class SearchArticleItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("image_urls") val image_urls: List<String>? = null,
        @SerializedName("author_name") val author_name: String? = null,
        @SerializedName("view") val view: Int = 0
    )

    internal data class SearchLiveItem(
        @SerializedName("roomid") val roomid: Long = 0,
        @SerializedName("short_id") val short_id: Long = 0,
        @SerializedName("uid") val uid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("online") val online: Int = 0,
        @SerializedName("user_cover") val user_cover: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("live_status") val live_status: Int = 0
    )

    internal data class SearchBangumiItem(
        @SerializedName("media_id") val media_id: Long = 0,
        @SerializedName("season_id") val season_id: Long = 0,
        @SerializedName("ep_id") val ep_id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("pic") val pic: String? = null,
        @SerializedName("areas") val areas: JsonElement? = null,
        @SerializedName("styles") val styles: JsonElement? = null,
        @SerializedName("index_show") val index_show: String? = null,
        @SerializedName("type") val type: String? = null
    )

    internal data class SearchTypeData(
        @SerializedName("seid") val seid: String? = null,
        @SerializedName("page") val page: Int = 0,
        @SerializedName("pagesize") val pagesize: Int = 0,
        @SerializedName("numResults") val numResults: Int = 0,
        @SerializedName("result") val result: JsonElement? = null
    )

    internal data class SearchResult(
        @SerializedName("type") val type: String? = null,
        @SerializedName("data") val data: JsonElement? = null
    )

    internal data class TagItem(
        @SerializedName("tag_id") val tag_id: Long = 0,
        @SerializedName("tag_name") val tag_name: String? = null
    )

    internal data class DefaultSearchData(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("show_name") val show_name: String? = null
    )

    internal data class SearchAllData(
        @SerializedName("result") val result: List<SearchResultGroup>? = null
    )

    suspend fun search(keyword: String, page: Int): List<JsonElement>? = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "keyword" to keyword,
            "page" to page.toString()
        ))
        val jsonElement = when (val result = api.searchAll(params)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext null
        }
        val type = object : TypeToken<ApiResponse<SearchAllData>>() {}.type
        val resp: ApiResponse<SearchAllData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext null
        val flattened = mutableListOf<JsonElement>()
        for (group in resp.data.result ?: return@withContext null) {
            val resultType = group.result_type.ifEmpty { continue }
            for (item in group.data) {
                if (item.isJsonObject) {
                    val obj = item.asJsonObject.deepCopy()
                    obj.addProperty("type", resultType)
                    flattened.add(obj)
                }
            }
        }
        flattened
    }

    suspend fun searchType(keyword: String, page: Int, type: String): JsonElement? = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "keyword" to keyword,
            "page" to page.toString(),
            "search_type" to type
        ))
        val jsonElement = when (val result = api.searchType(params)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext null
        }
        val resp: ApiResponse<SearchTypeData>? = GsonConfig.gson.fromJson(jsonElement, object : TypeToken<ApiResponse<SearchTypeData>>() {}.type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext null
        resp.data.result
    }

    suspend fun getVideosFromSearchResult(input: List<JsonElement>?, first: Boolean = false): List<VideoCard> = withContext(Dispatchers.IO) {
        if (input == null) return@withContext emptyList()
        val result = mutableListOf<VideoCard>()
        for (el in input) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString
            if (type != "video") continue
            
            val item = try { 
                GsonConfig.gson.fromJson(obj, SearchVideoItem::class.java) 
            } catch (e: Exception) { 
                null 
            } ?: continue
            
            // For play count, try to handle string/int safely
            val playCountStr = try {
                val playEl = obj.get("play")
                if (playEl != null && playEl.isJsonPrimitive) {
                    if (playEl.asJsonPrimitive.isNumber) {
                        StringUtil.toWan(playEl.asLong)
                    } else {
                        playEl.asString
                    }
                } else {
                    ""
                }
            } catch (e: Exception) { "" }
            
            result.add(VideoCard(
                title = htmlToString(item.title ?: ""),
                upName = item.author ?: "",
                view = playCountStr.ifEmpty { StringUtil.toWan(item.play.toLong()) },
                cover = fixCoverUrl(item.pic ?: ""),
                aid = item.aid,
                bvid = item.bvid ?: "",
                cid = item.cid
            ))
            if (first && result.isNotEmpty()) break
        }
        result
    }

    suspend fun getUsersFromSearchResult(input: List<JsonElement>?): List<UserInfo> = withContext(Dispatchers.IO) {
        if (input == null) return@withContext emptyList()
        val deferreds = mutableListOf<Deferred<UserInfo>>()
        for (el in input) {
            if (!el.isJsonObject) continue
            val item = GsonConfig.gson.fromJson(el, SearchUserItem::class.java) ?: continue
            deferreds.add(async {
                val fullInfo = try {
                    UserInfoApi.getUserInfo(item.mid)
                } catch (e: Exception) { null }

                val vipStatus = fullInfo?.vip_role ?: 0
                val officialType = fullInfo?.official ?: -1
                val officialDesc = fullInfo?.officialDesc ?: ""

                UserInfo(
                    mid = item.mid,
                    name = item.uname ?: "",
                    sign = item.usign ?: "",
                    fans = item.fans,
                    level = item.level,
                    avatar = fixCoverUrl(item.upic ?: ""),
                    official = officialType,
                    officialDesc = officialDesc,
                    vip_role = vipStatus,
                    is_senior_member = fullInfo?.is_senior_member ?: 0
                )
            })
        }
        deferreds.awaitAll()
    }

    suspend fun getArticlesFromSearchResult(input: List<JsonElement>?): List<ArticleCard> = withContext(Dispatchers.IO) {
        if (input == null) return@withContext emptyList()
        val result = mutableListOf<ArticleCard>()
        for (el in input) {
            if (!el.isJsonObject) continue
            val item = GsonConfig.gson.fromJson(el, SearchArticleItem::class.java) ?: continue
            result.add(ArticleCard(
                title = htmlToString(item.title ?: ""),
                id = item.id,
                cover = fixCoverUrl(item.image_urls?.firstOrNull() ?: ""),
                upName = item.author_name ?: "",
                view = StringUtil.toWan(item.view.toLong())
            ))
        }
        result
    }

    suspend fun getLiveFromSearchResult(input: List<JsonElement>?): List<LiveRoom> = withContext(Dispatchers.IO) {
        if (input == null) return@withContext emptyList()
        val result = mutableListOf<LiveRoom>()
        for (el in input) {
            if (!el.isJsonObject) continue
            val item = GsonConfig.gson.fromJson(el, SearchLiveItem::class.java) ?: continue
            val finalRoomId = if (item.roomid <= 0) item.short_id else item.roomid
            result.add(LiveRoom(
                roomid = finalRoomId,
                short_id = item.short_id,
                uid = item.uid,
                title = htmlToString(item.title ?: ""),
                uname = item.uname ?: "",
                online = item.online,
                user_cover = fixCoverUrl(item.user_cover ?: ""),
                cover = fixCoverUrl(item.cover ?: ""),
                live_status = item.live_status
            ))
        }
        result
    }

    suspend fun getBangumisFromSearchResult(input: List<JsonElement>?): List<VideoCard> = withContext(Dispatchers.IO) {
        if (input == null) return@withContext emptyList()
        val result = mutableListOf<VideoCard>()
        for (el in input) {
            if (!el.isJsonObject) continue
            val item = try { GsonConfig.gson.fromJson(el, SearchBangumiItem::class.java) } catch (e: Exception) { null } ?: continue
            
            val areaStr = if (item.areas != null && item.areas.isJsonPrimitive) item.areas.asString else ""
            val idToUse = if (item.season_id > 0) item.season_id else if (item.media_id > 0) item.media_id else item.ep_id
            if (idToUse <= 0) continue

            result.add(VideoCard(
                title = htmlToString(item.title ?: ""),
                upName = item.index_show ?: areaStr,
                view = "",
                cover = fixCoverUrl(item.cover ?: item.pic ?: ""),
                aid = idToUse,
                bvid = "",
                cid = item.season_id,
                type = "bangumi"
            ))
        }
        result
    }

    internal data class SuggestData(
        @SerializedName("tag") val tag: List<SuggestTag>? = null
    )

    internal data class SuggestTag(
        @SerializedName("value") val value: String? = null
    )

    suspend fun getSearchSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        val jsonElement = api.getSearchSuggest(keyword)
        val resp: SuggestData? = GsonConfig.gson.fromJson(jsonElement, SuggestData::class.java)
        resp?.tag?.mapNotNull { it.value?.takeIf { v -> v.isNotEmpty() } } ?: emptyList()
    }

    suspend fun getDefaultSearchContent(): String? = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getDefaultSearch()) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext null
        }
        val resp: ApiResponse<DefaultSearchData>? = GsonConfig.gson.fromJson(jsonElement, object : TypeToken<ApiResponse<DefaultSearchData>>() {}.type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext null
        resp.data.show_name
    }

    private fun htmlToString(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")

    private fun fixCoverUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("http://")) return url.replaceFirst("http://", "https://")
        return url
    }
}
