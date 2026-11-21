package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class CoreHistoryItem(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val author: String,
    val progressSeconds: Long,
    val durationSeconds: Long,
    val progressPercent: Float,
)

data class CoreFavFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
    val cover: String,
)

data class CoreFavVideoPage(val videos: List<CoreVideo>, val hasMore: Boolean)
data class CoreHistoryCursor(val max: Long = 0, val viewAt: Long = 0, val business: String = "")
data class CoreHistoryPage(val items: List<CoreHistoryItem>, val next: CoreHistoryCursor?)
data class CoreDynamicVideoPage(val videos: List<CoreVideo>, val nextOffset: String?)

data class CoreFollowUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
    val fans: Int = 0,
)

data class CoreFollowPage(val items: List<CoreFollowUser>, val total: Int)

interface UserDataGateway {
    suspend fun loadHistory(cookie: String): List<CoreHistoryItem>
    suspend fun loadHistoryPage(cookie: String, cursor: CoreHistoryCursor = CoreHistoryCursor()): CoreHistoryPage = CoreHistoryPage(loadHistory(cookie), null)
    suspend fun loadWatchLater(cookie: String): List<CoreVideo>
    suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String): Unit = error("暂不支持修改稍后再看")
    suspend fun loadFavFolders(cookie: String, mid: Long): List<CoreFavFolder>
    suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int): List<CoreVideo>
    suspend fun loadFavVideoPage(cookie: String, folderId: Long, page: Int): CoreFavVideoPage {
        val videos = loadFavVideos(cookie, folderId, page)
        return CoreFavVideoPage(videos, videos.size >= 20)
    }
    suspend fun loadDynamicVideos(cookie: String, offset: String = ""): List<CoreVideo>
    suspend fun loadDynamicVideoPage(cookie: String, offset: String = ""): CoreDynamicVideoPage = CoreDynamicVideoPage(loadDynamicVideos(cookie, offset), null)
    suspend fun loadFollowingList(mid: Long, page: Int, cookie: String = ""): CoreFollowPage = error("暂不支持关注列表")
    suspend fun loadFollowerList(mid: Long, page: Int, cookie: String = ""): CoreFollowPage = error("暂不支持粉丝列表")
}

class BilibiliUserDataGateway(private val client: OkHttpClient = OkHttpClient()) : UserDataGateway {
    override suspend fun loadHistory(cookie: String): List<CoreHistoryItem> = loadHistoryPage(cookie).items

    override suspend fun loadHistoryPage(cookie: String, cursor: CoreHistoryCursor): CoreHistoryPage {
        val url = "https://api.bilibili.com/x/web-interface/history/cursor".toHttpUrl().newBuilder()
            .addQueryParameter("max", cursor.max.toString())
            .addQueryParameter("view_at", cursor.viewAt.toString())
            .addQueryParameter("business", cursor.business).build()
        val data = getWithCookie(url.toString(), cookie).requireBilibiliData()
        val list = data.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
        val items = list?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val historyObj = obj.getAsJsonObject("history")
            val bvid = obj.string("bvid") ?: historyObj?.string("bvid") ?: return@mapNotNull null
            val aid = obj.get("aid")?.asLong ?: historyObj?.get("oid")?.asLong ?: 0L
            val cid = historyObj?.get("cid")?.asLong ?: obj.get("cid")?.asLong ?: 0L
            val duration = obj.get("duration")?.asLong ?: 0L
            val progress = obj.get("progress")?.asLong ?: 0L
            val percent = when {
                progress == -1L || (duration > 0 && progress >= duration * 0.95) -> 1f
                duration > 0 && progress > 0 -> (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }
            CoreHistoryItem(
                aid = aid,
                bvid = bvid,
                cid = cid,
                title = obj.string("title").orEmpty(),
                cover = normalizePic(obj.string("cover")),
                author = obj.string("author_name").orEmpty(),
                progressSeconds = progress,
                durationSeconds = duration,
                progressPercent = percent,
            )
        }.orEmpty()
        val nextData = data.get("cursor")?.takeIf { it.isJsonObject }?.asJsonObject
        val next = nextData?.let {
            CoreHistoryCursor(it.get("max")?.asLong ?: 0, it.get("view_at")?.asLong ?: 0, it.string("business").orEmpty())
        }?.takeIf { it.max > 0 && it != cursor && (list?.size() ?: 0) > 0 && nextData.get("is_end")?.asBoolean != true }
        return CoreHistoryPage(items, next)
    }

    override suspend fun loadWatchLater(cookie: String): List<CoreVideo> {
        val json = getWithCookie("https://api.bilibili.com/x/v2/history/toview", cookie)
        val data = json.requireBilibiliData()
        val list = data.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val bvid = obj.string("bvid") ?: return@mapNotNull null
            val owner = obj.getAsJsonObject("owner")
            CoreVideo(
                id = obj.get("aid")?.asLong ?: 0L,
                bvid = bvid,
                title = obj.string("title").orEmpty(),
                cover = normalizePic(obj.string("pic")),
                author = owner?.string("name").orEmpty(),
                cid = obj.get("cid")?.asLong ?: 0L,
            )
        }
    }

    override suspend fun loadFavFolders(cookie: String, mid: Long): List<CoreFavFolder> {
        val url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid"
        val json = getWithCookie(url, cookie)
        val data = json.requireBilibiliData()
        val list = data.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            CoreFavFolder(
                id = id,
                title = obj.string("title").orEmpty(),
                mediaCount = obj.get("media_count")?.asInt ?: 0,
                cover = normalizePic(obj.string("cover")),
            )
        }
    }

    override suspend fun loadFavVideos(cookie: String, folderId: Long, page: Int): List<CoreVideo> =
        loadFavVideoPage(cookie, folderId, page).videos

    override suspend fun loadFavVideoPage(cookie: String, folderId: Long, page: Int): CoreFavVideoPage {
        require(folderId > 0 && page > 0)
        val url = "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$folderId&pn=$page&ps=20"
        val data = getWithCookie(url, cookie).requireBilibiliData()
        val medias = data.get("medias")?.takeIf { it.isJsonArray }?.asJsonArray
        val videos = medias?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val bvid = obj.string("bv_id").orEmpty().ifBlank { obj.string("bvid").orEmpty() }
            if (bvid.isBlank()) return@mapNotNull null
            val upper = obj.getAsJsonObject("upper")
            CoreVideo(
                id = obj.get("id")?.asLong ?: 0L,
                bvid = bvid,
                title = obj.string("title").orEmpty(),
                cover = normalizePic(obj.string("cover")),
                author = upper?.string("name").orEmpty(),
            )
        }.orEmpty().distinctBy { it.bvid }
        return CoreFavVideoPage(videos, data.get("has_more")?.takeUnless { it.isJsonNull }?.asBoolean ?: (medias?.size() == 20))
    }

    override suspend fun loadDynamicVideos(cookie: String, offset: String): List<CoreVideo> = loadDynamicVideoPage(cookie, offset).videos

    override suspend fun loadDynamicVideoPage(cookie: String, offset: String): CoreDynamicVideoPage {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all".toHttpUrl().newBuilder()
            .addQueryParameter("timezone_offset", "-480").addQueryParameter("type", "video")
            .apply { if (offset.isNotBlank()) addQueryParameter("offset", offset) }.build()
        val data = getWithCookie(url.toString(), cookie).requireBilibiliData()
        val items = data.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
        val videos = items?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val modules = obj.getAsJsonObject("modules") ?: return@mapNotNull null
            val moduleAuthor = modules.getAsJsonObject("module_author")
            val moduleDynamic = modules.getAsJsonObject("module_dynamic")
            val major = moduleDynamic?.getAsJsonObject("major")
            val archive = major?.getAsJsonObject("archive") ?: return@mapNotNull null
            val bvid = archive.string("bvid") ?: return@mapNotNull null
            CoreVideo(
                id = archive.get("aid")?.asLong ?: 0L,
                bvid = bvid,
                title = archive.string("title").orEmpty(),
                cover = normalizePic(archive.string("cover")),
                author = moduleAuthor?.string("name").orEmpty(),
                durationText = archive.string("duration_text").orEmpty(),
            )
        }.orEmpty().distinctBy { it.bvid }
        val more = data.get("has_more")?.takeUnless { it.isJsonNull }?.let { if (it.asJsonPrimitive.isBoolean) it.asBoolean else it.asInt != 0 } ?: false
        val next = data.string("offset")?.takeIf { more && it.isNotBlank() && it != offset }
        return CoreDynamicVideoPage(videos, next)
    }

    override suspend fun changeWatchLater(aid: Long, add: Boolean, cookie: String, csrf: String) {
        require(aid > 0 && cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val form = FormBody.Builder().add("aid", aid.toString()).add("csrf", csrf).build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v2/history/toview/${if (add) "add" else "del"}")
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BILIBILI_USER_AGENT)
            .header("Cookie", cookie)
            .post(form).build()
        val json = execute(request)
        check(json.get("code")?.asInt == 0) { "稍后再看操作未完成，请刷新后重试" }
    }

    override suspend fun loadFollowingList(mid: Long, page: Int, cookie: String): CoreFollowPage =
        loadFollowPage("followings", mid, page, cookie)

    override suspend fun loadFollowerList(mid: Long, page: Int, cookie: String): CoreFollowPage =
        loadFollowPage("followers", mid, page, cookie)

    private suspend fun loadFollowPage(endpoint: String, mid: Long, page: Int, cookie: String): CoreFollowPage {
        require(mid > 0 && page > 0)
        val data = getWithCookie(
            "https://api.bilibili.com/x/relation/$endpoint?vmid=$mid&pn=$page&ps=20&order=desc",
            cookie,
        ).requireBilibiliData()
        val items = data.getAsJsonArray("list")
            ?.mapNotNull { entry -> entry.takeIf { it.isJsonObject }?.asJsonObject }
            .orEmpty()
        val users = items.mapNotNull { item ->
            val itemMid = item.get("mid")?.takeUnless { it.isJsonNull }?.asLong ?: return@mapNotNull null
            if (itemMid <= 0L) return@mapNotNull null
            CoreFollowUser(
                mid = itemMid,
                name = item.string("uname").orEmpty(),
                face = normalizePic(item.string("face")).orEmpty(),
                sign = item.string("sign").orEmpty(),
                fans = item.get("follower")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
            )
        }
        val total = data.get("total")?.takeUnless { it.isJsonNull }?.asInt ?: users.size
        return CoreFollowPage(users, total)
    }

    private suspend fun getWithCookie(url: String, cookie: String): JsonObject {
        val request = Request.Builder().url(url)
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BILIBILI_USER_AGENT)
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): JsonObject {
        val body = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            check(it.isSuccessful) { "HTTP ${it.code}" }
                            it.body.string()
                        }
                        continuation.resume(text)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
        return JsonParser.parseString(body).asJsonObject
    }

    private fun JsonObject.string(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun normalizePic(value: String?) = when {
        value.isNullOrBlank() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        else -> value
    }
}
