package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.model.Collection as BiliCollection
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

object FavoriteApi {

    private val api by lazy { BiliApiService.create() }

    internal data class FavFolderListData(
        @SerializedName("list") val list: List<FavFolderItem>? = null
    )

    internal data class FavFolderItem(
        @SerializedName("fav_box") val fav_box: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("cur_count") val cur_count: Int = 0,
        @SerializedName("max_count") val max_count: Int = 0
    )

    internal data class V3FavFolderData(
        @SerializedName("list") val list: List<V3FavFolderItem>? = null
    )

    internal data class V3FavFolderItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("media_count") val media_count: Int = 0,
        @SerializedName("attr") val attr: Int = 0
    )

    internal data class FavFolderVideosData(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("medias") val medias: List<FavArchiveItem>? = null
    )

    internal data class FavArchiveItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("upper") val upper: FavUpperItem? = null,
        @SerializedName("cnt_info") val cnt_info: FavCntInfo? = null,
        @SerializedName("bv_id") val bv_id: String? = null
    )

    internal data class FavUpperItem(
        @SerializedName("name") val name: String? = null
    )

    internal data class FavCntInfo(
        @SerializedName("play") val play: Int = 0
    )

    internal data class CollectedListData(
        @SerializedName("list") val list: List<CollectionItem>? = null
    )

    internal data class CollectionItem(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("media_count") val media_count: Int = 0
    )

    internal data class OpusFavData(
        @SerializedName("items") val items: List<OpusItem>? = null,
        @SerializedName("has_more") val has_more: Boolean = false
    )

    internal data class OpusItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("image_urls") val image_urls: List<String>? = null,
        @SerializedName("pub_time") val pub_time: Long = 0,
        @SerializedName("author") val author: OpusAuthor? = null,
        @SerializedName("stat") val stat: OpusStat? = null
    )

    internal data class OpusAuthor(
        @SerializedName("name") val name: String? = null
    )

    internal data class OpusStat(
        @SerializedName("view") val view: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("reply") val reply: Int = 0
    )

    internal data class FavStateData(
        @SerializedName("list") val list: List<FavStateItem>? = null
    )

    internal data class FavStateItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("fav_state") val fav_state: Int = 0,
        @SerializedName("media_count") val media_count: Int = 0
    )

    data class FavFolderUI(
        val id: Long,
        val name: String,
        val mediaCount: Int,
        val isFav: Boolean
    )

    suspend fun getFavoriteFolders(mid: Long): List<FavoriteFolder> = withContext(Dispatchers.IO) {
        val v2List = try {
            val json = httpGet("https://api.bilibili.com/space.bilibili.com/ajax/fav/getBoxList?mid=$mid")
            val resp: ApiResponse<FavFolderListData>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<FavFolderListData>>() {}.type)
            resp?.data?.list?.filterNotNull()?.map {
                FavoriteFolder(id = it.fav_box, mediaId = it.fav_box, name = it.name ?: "", videoCount = it.cur_count, maxCount = it.max_count, isDefault = it.fav_box == mid)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val v3List = try {
            when (val resp = api.getFavFolders(2, mid)) {
                is Result.Success -> {
                    val parsed: ApiResponse<V3FavFolderData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<V3FavFolderData>>() {}.type)
                    parsed?.data?.list?.filterNotNull()?.map {
                        FavoriteFolder(id = it.id, mediaId = it.id, name = it.title ?: "", cover = it.cover ?: "", videoCount = it.media_count, isDefault = it.attr and 1 == 1)
                    } ?: emptyList()
                }
                is Result.Error -> emptyList()
            }
        } catch (_: Exception) { emptyList() }

        (v2List + v3List).sortedByDescending { it.isDefault }
    }

    suspend fun getFavoritedCollections(mid: Long, page: Int): Pair<Boolean, List<BiliCollection>> = withContext(Dispatchers.IO) {
        when (val resp = api.getCollectedFolders(mid, page, 20)) {
            is Result.Success -> {
                val parsed: ApiResponse<CollectedListData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<CollectedListData>>() {}.type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext Pair(false, emptyList())
                val hasMore = parsed.data.list != null && parsed.data.list.size >= 20
                val collections = parsed.data.list?.filterNotNull()?.map {
                    BiliCollection(id = it.id, title = it.title ?: "", mid = it.mid, cover = it.cover ?: "", view = it.media_count.toString())
                } ?: emptyList()
                Pair(hasMore, collections)
            }
            is Result.Error -> Pair(false, emptyList())
        }
    }

    suspend fun getFolderVideos(mid: Long, fid: Long, page: Int): Pair<Int, List<VideoCard>> = withContext(Dispatchers.IO) {
        when (val resp = api.getFavVideos(mid, fid, page, 20)) {
            is Result.Success -> {
                val parsed: ApiResponse<FavFolderVideosData>? = GsonConfig.gson.fromJson(resp.data, object : TypeToken<ApiResponse<FavFolderVideosData>>() {}.type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext Pair(0, emptyList())
                val videos = parsed.data.medias?.filterNotNull()?.map {
                    VideoCard(title = it.title ?: "", upName = it.upper?.name ?: "", view = StringUtil.toWan(it.cnt_info?.play?.toLong() ?: 0), cover = it.cover ?: "", aid = it.id, bvid = it.bv_id ?: "")
                } ?: emptyList()
                Pair(parsed.data.count, videos)
            }
            is Result.Error -> Pair(0, emptyList())
        }
    }

    suspend fun getFavouriteOpus(page: Int): Pair<Boolean, List<Opus>> = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/favlist?pn=$page&ps=20"
        val json = httpGet(url)
        val resp: ApiResponse<OpusFavData>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<OpusFavData>>() {}.type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair(false, emptyList())
        val opusList = resp.data.items?.filterNotNull()?.map {
            Opus(id = it.id, type = it.type, title = it.title ?: "", cover = it.image_urls?.firstOrNull() ?: "", pubTime = if (it.pub_time > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(it.pub_time * 1000) else "", upInfo = UserInfo(name = it.author?.name ?: ""), stats = Stats(view = it.stat?.view ?: 0, like = it.stat?.like ?: 0, reply = it.stat?.reply ?: 0))
        } ?: emptyList()
        Pair(resp.data.has_more, opusList)
    }

    suspend fun getFavoriteState(aid: Long): List<FavFolderUI> = withContext(Dispatchers.IO) {
        val mid = CookieManager.getInfoFromCookie("DedeUserID")
        val url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&rid=$aid&up_mid=$mid"
        val json = httpGet(url)
        val resp: ApiResponse<FavStateData>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<FavStateData>>() {}.type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        val folders = mutableListOf<FavFolderUI>()
        resp.data.list?.filterNotNull()?.forEach {
            folders.add(FavFolderUI(it.id, it.title ?: "", it.media_count, it.fav_state == 1))
        }
        folders
    }

    suspend fun addFavorite(aid: Long, fid: Long): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("rid", aid.toString())
            .add("type", "2")
            .add("add_media_ids", fid.toString())
            .add("del_media_ids", "")
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v3/fav/resource/deal")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<*>>() {}.type)
        resp?.code ?: -1
    }

    suspend fun deleteFavorite(aid: Long, fid: Long): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("resources", "$aid:2")
            .add("media_id", fid.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/medialist/gateway/coll/resource/batch/del")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<*>>() {}.type)
        resp?.code ?: -1
    }

    suspend fun addFolder(title: String, intro: String, privacy: Int): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("title", title)
            .add("intro", intro)
            .add("privacy", privacy.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v3/fav/folder/add")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<*>>() {}.type)
        resp?.code ?: -1
    }

    suspend fun editFolder(mediaId: Long, title: String, intro: String, privacy: Int): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("media_id", mediaId.toString())
            .add("title", title)
            .add("intro", intro)
            .add("privacy", privacy.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v3/fav/folder/edit")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<*>>() {}.type)
        resp?.code ?: -1
    }

    suspend fun deleteFolder(mediaId: Long): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("media_id", mediaId.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v3/fav/folder/del")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, object : TypeToken<ApiResponse<*>>() {}.type)
        resp?.code ?: -1
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
