package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class CoreVideoPage(
    val page: Int,
    val part: String,
    val cid: Long,
    val durationSeconds: Int,
)

data class CoreVideoReply(
    val rpid: Long,
    val uname: String,
    val avatar: String,
    val message: String,
    val likeCount: Long,
    val replyCount: Int = 0,
    val pinned: Boolean = false,
)

data class CoreReplyThreadPage(val root: CoreVideoReply?, val replies: List<CoreVideoReply>, val hasMore: Boolean)

data class CoreReplyPage(val replies: List<CoreVideoReply>, val nextOffset: String?, val totalCount: Int)

data class CoreVideoDetails(
    val video: CoreVideo,
    val description: String,
    val durationSeconds: Int,
    val viewCount: Long,
    val likeCount: Long,
    val replyCount: Long,
    val pages: List<CoreVideoPage> = emptyList(),
    val ownerMid: Long = 0L,
    val ownerAvatar: String = "",
    val collectionTitle: String = "",
    val collectionVideos: List<CoreVideo> = emptyList(),
)

data class CoreTripleResult(val liked: Boolean, val coined: Boolean, val favorited: Boolean, val coinCount: Int)
data class CoreVideoRelation(val liked: Boolean, val coinCount: Int, val favorited: Boolean)

class CoreReplyRejectedException(message: String) : IllegalStateException(message)

interface VideoGateway {
    suspend fun sendReply(aid: Long, root: Long, parent: Long, message: String, cookie: String, csrf: String): Long = error("暂不支持发送评论")
    suspend fun loadDetails(video: CoreVideo): CoreVideoDetails
    suspend fun loadReplyPage(aid: Long, offset: String = ""): CoreReplyPage = CoreReplyPage(loadReplies(aid), null, 0)
    suspend fun loadReplyPage(aid: Long, offset: String, cookie: String): CoreReplyPage = loadReplyPage(aid, offset)
    suspend fun loadReplies(aid: Long): List<CoreVideoReply> = emptyList()
    suspend fun loadReplyThread(aid: Long, root: Long, page: Int, cookie: String): CoreReplyThreadPage = error("暂不支持评论回复")
    suspend fun loadRelation(aid: Long, cookie: String): CoreVideoRelation = error("暂不支持读取操作状态")
    suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String): Boolean = false
    suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String): Boolean = false
    suspend fun favVideo(aid: Long, folderId: Long, cookie: String, csrf: String): Boolean = false
    suspend fun tripleVideo(aid: Long, cookie: String, csrf: String): CoreTripleResult = error("暂不支持三连")
}

class BilibiliVideoGateway(
    private val client: OkHttpClient = OkHttpClient(),
) : VideoGateway {
    override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails {
        val url = "https://api.bilibili.com/x/web-interface/view".toHttpUrl()
            .newBuilder()
            .addQueryParameter("bvid", video.bvid)
            .build()
        val data = client.getBilibiliJson(url.toString()).requireBilibiliData()
        val owner = data.getAsJsonObject("owner")
        val stat = data.getAsJsonObject("stat")
        val pagesArray = data.getAsJsonArray("pages")
        val pages = pagesArray?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val cid = obj.long("cid") ?: return@mapNotNull null
            CoreVideoPage(
                page = obj.get("page")?.asInt ?: 1,
                part = obj.string("part").orEmpty(),
                cid = cid,
                durationSeconds = obj.get("duration")?.asInt ?: 0,
            )
        }.orEmpty()

        val resolved = video.copy(
            id = data.long("aid") ?: video.id,
            title = data.string("title")?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty().ifBlank { video.title },
            cover = normalizeUrl(data.string("pic")).ifBlank { video.cover },
            author = owner?.string("name").orEmpty().ifBlank { video.author },
            cid = data.long("cid") ?: video.cid,
        )
        val season = data.get("ugc_season")?.takeIf { it.isJsonObject }?.asJsonObject
        val collection = season?.get("sections")?.takeIf { it.isJsonArray }?.asJsonArray?.flatMap { section ->
            section.takeIf { it.isJsonObject }?.asJsonObject?.get("episodes")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { entry ->
                val episode = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val archive = episode.get("arc")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()
                listOf("aid", "bvid", "cid", "title").forEach { key -> if (!archive.has(key)) episode.get(key)?.let { archive.add(key, it) } }
                archive.coreArchiveVideo()
            }.orEmpty()
        }.orEmpty().distinctBy { it.bvid }
        return CoreVideoDetails(
            video = resolved,
            description = data.string("desc").orEmpty(),
            durationSeconds = data.get("duration")?.asInt ?: 0,
            viewCount = stat?.long("view") ?: 0,
            likeCount = stat?.long("like") ?: 0,
            replyCount = stat?.long("reply") ?: 0,
            pages = pages,
            ownerMid = owner?.long("mid") ?: 0L,
            ownerAvatar = normalizeUrl(owner?.string("face")),
            collectionTitle = season?.string("title").orEmpty(),
            collectionVideos = collection,
        )
    }

    override suspend fun loadReplies(aid: Long): List<CoreVideoReply> = loadReplyPage(aid).replies

    override suspend fun loadReplyPage(aid: Long, offset: String): CoreReplyPage = loadReplyPage(aid, offset, "")

    override suspend fun loadReplyPage(aid: Long, offset: String, cookie: String): CoreReplyPage {
        if (aid <= 0) return CoreReplyPage(emptyList(), null, 0)
        val url = client.signedBilibiliUrl(
            "https://api.bilibili.com/x/v2/reply/wbi/main",
            mapOf(
                "oid" to aid.toString(), "type" to "1", "mode" to "3",
                "pagination_str" to JsonObject().apply { add("offset", JsonPrimitive(offset)) }.toString(),
                "plat" to "1", "web_location" to "1315875",
            ),
        )
        val data = client.getBilibiliJson(url, cookie).requireBilibiliData()
        val top = data.get("top")?.takeIf { it.isJsonObject }?.asJsonObject
        val topReplies = data.get("top_replies")
        val pinnedObjects = when {
            topReplies?.isJsonArray == true -> topReplies.asJsonArray.toList()
            topReplies?.isJsonObject == true -> listOf(topReplies)
            else -> emptyList()
        } + listOfNotNull(top?.get("admin"), top?.get("upper"), top?.get("vote"))
        val pinned = if (offset.isEmpty()) pinnedObjects.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject?.toReply()?.copy(pinned = true) } else emptyList()
        val replies = data.get("replies")?.takeIf { it.isJsonArray }?.asJsonArray
        val mapped = (pinned + replies?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            obj.toReply()
        }.orEmpty()).distinctBy { it.rpid }
        val cursor = data.get("cursor")?.takeIf { it.isJsonObject }?.asJsonObject
        val next = cursor?.get("pagination_reply")?.takeIf { it.isJsonObject }?.asJsonObject?.string("next_offset")
        val total = cursor?.get("all_count")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        val ended = cursor?.get("is_end")?.takeUnless { it.isJsonNull }?.asBoolean == true
        return CoreReplyPage(mapped, next?.takeIf { !ended && it.isNotBlank() && it != offset }, total)
    }

    override suspend fun loadReplyThread(aid: Long, root: Long, page: Int, cookie: String): CoreReplyThreadPage {
        require(aid > 0 && root > 0 && page > 0)
        val url = "https://api.bilibili.com/x/v2/reply/reply".toHttpUrl().newBuilder()
            .addQueryParameter("oid", aid.toString()).addQueryParameter("type", "1")
            .addQueryParameter("root", root.toString()).addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", "20").build()
        val data = client.getBilibiliJson(url.toString(), cookie).requireBilibiliData()
        val replies = data.get("replies")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
            it.takeIf { item -> item.isJsonObject }?.asJsonObject?.toReply()
        }.orEmpty().distinctBy { it.rpid }
        val parent = data.get("root")?.takeIf { it.isJsonObject }?.asJsonObject?.toReply()
        val info = data.get("page")?.takeIf { it.isJsonObject }?.asJsonObject
        val count = info?.get("count")?.asInt ?: replies.size
        val size = info?.get("size")?.asInt?.coerceAtLeast(1) ?: 20
        return CoreReplyThreadPage(parent, replies, replies.isNotEmpty() && page.toLong() * size < count)
    }

    override suspend fun sendReply(aid: Long, root: Long, parent: Long, message: String, cookie: String, csrf: String): Long {
        require(aid > 0 && root >= 0 && parent >= 0 && ((root == 0L && parent == 0L) || (root > 0 && parent > 0)))
        require(cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        require(message.isNotBlank() && message.codePointCount(0, message.length) <= 1000) { "评论需为 1–1000 字" }
        val body = FormBody.Builder().add("oid", aid.toString()).add("type", "1")
            .add("root", root.toString()).add("parent", parent.toString())
            .add("message", message).add("plat", "1").add("csrf", csrf).build()
        val json = postForm("https://api.bilibili.com/x/v2/reply/add", body, cookie, retry = false)
        val code = json.get("code")?.takeUnless { it.isJsonNull }?.asInt
        if (code != null && code != 0) throw CoreReplyRejectedException(json.string("message").orEmpty().ifBlank { "评论未被接受（$code）" })
        check(code == 0) { "发送结果未确认" }
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        if (data?.get("need_captcha")?.takeUnless { it.isJsonNull }?.asBoolean == true) {
            throw CoreReplyRejectedException("需要验证码，请使用哔哩哔哩客户端完成验证")
        }
        return checkNotNull(data?.long("rpid")?.takeIf { it > 0 }) { "发送结果未确认" }
    }

    private fun JsonObject.toReply(): CoreVideoReply? {
        val id = long("rpid")?.takeIf { it > 0 } ?: return null
        val member = get("member")?.takeIf { it.isJsonObject }?.asJsonObject
        val content = get("content")?.takeIf { it.isJsonObject }?.asJsonObject
        return CoreVideoReply(id, member?.string("uname").orEmpty(), normalizeUrl(member?.string("avatar")),
            content?.string("message").orEmpty(), long("like") ?: 0,
            get("rcount")?.takeUnless { it.isJsonNull }?.asInt ?: 0)
    }

    override suspend fun loadRelation(aid: Long, cookie: String): CoreVideoRelation {
        require(aid > 0 && cookie.isNotBlank()) { "请先登录" }
        val data = client.getBilibiliJson("https://api.bilibili.com/x/web-interface/archive/relation?aid=$aid", cookie).requireBilibiliData()
        return CoreVideoRelation(
            liked = data.get("like")?.asBoolean ?: false,
            coinCount = data.get("coin")?.asInt ?: 0,
            favorited = data.get("favorite")?.asBoolean ?: false,
        )
    }

    override suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String): Boolean {
        if (cookie.isBlank() || csrf.isBlank()) return false
        val form = FormBody.Builder()
            .add("aid", aid.toString())
            .add("like", if (like) "1" else "2")
            .add("csrf", csrf)
            .build()
        val json = postForm("https://api.bilibili.com/x/web-interface/archive/like", form, cookie)
        return json.get("code")?.asInt == 0
    }

    override suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String): Boolean {
        if (cookie.isBlank() || csrf.isBlank()) return false
        val form = FormBody.Builder()
            .add("aid", aid.toString())
            .add("multiply", count.coerceIn(1, 2).toString())
            .add("csrf", csrf)
            .build()
        val json = postForm("https://api.bilibili.com/x/web-interface/coin/add", form, cookie)
        return json.get("code")?.asInt == 0
    }

    override suspend fun favVideo(aid: Long, folderId: Long, cookie: String, csrf: String): Boolean {
        if (cookie.isBlank() || csrf.isBlank() || folderId <= 0 || aid <= 0) return false
        val form = FormBody.Builder()
            .add("rid", aid.toString())
            .add("type", "2")
            .add("add_media_ids", folderId.toString())
            .add("del_media_ids", "")
            .add("csrf", csrf)
            .build()
        val json = postForm("https://api.bilibili.com/medialist/gateway/coll/resource/deal", form, cookie)
        return json.get("code")?.asInt == 0
    }

    override suspend fun tripleVideo(aid: Long, cookie: String, csrf: String): CoreTripleResult {
        require(aid > 0 && cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val body = FormBody.Builder().add("aid", aid.toString()).add("csrf", csrf).build()
        val data = postForm("https://api.bilibili.com/x/web-interface/archive/like/triple", body, cookie).requireBilibiliData()
        return CoreTripleResult(
            liked = data.get("like")?.asBoolean ?: false,
            coined = data.get("coin")?.asBoolean ?: false,
            favorited = data.get("fav")?.asBoolean ?: false,
            coinCount = data.get("multiply")?.asInt ?: 0,
        )
    }

    private suspend fun postForm(url: String, body: FormBody, cookie: String, retry: Boolean = true): JsonObject {
        val request = Request.Builder().url(url)
            .header("Referer", "https://www.bilibili.com/")
            .header("User-Agent", BILIBILI_USER_AGENT)
            .header("Cookie", cookie)
            .post(body)
            .build()
        val text = suspendCancellableCoroutine<String> { continuation ->
            val transport = if (retry) client else client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
            val call = transport.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val respBody = response.use {
                            check(it.isSuccessful) { "请求失败：HTTP ${it.code}" }
                            it.body.string()
                        }
                        continuation.resume(respBody)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
        return JsonParser.parseString(text).asJsonObject
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.long(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun normalizeUrl(value: String?): String = when {
        value.isNullOrBlank() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        else -> value
    }
}
