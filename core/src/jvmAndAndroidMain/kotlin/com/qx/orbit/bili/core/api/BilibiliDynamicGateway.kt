package com.qx.orbit.bili.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class BilibiliDynamicGateway(private val client: OkHttpClient = OkHttpClient()) : DynamicGateway {
    override suspend fun load(cookie: String, offset: String): CoreDynamicPage {
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all".toHttpUrl().newBuilder()
            .addQueryParameter("timezone_offset", "-480").addQueryParameter("type", "all")
            .addQueryParameter("features", "itemOpusStyle")
            .apply { if (offset.isNotBlank()) addQueryParameter("offset", offset) }.build()
        val data = client.getBilibiliJson(url.toString(), cookie).requireBilibiliData()
        val items = data.arrayObjects("items").mapNotNull { parse(it, 0) }.distinctBy { it.id }
        val hasMore = data.get("has_more")?.takeIf { it.isJsonPrimitive }?.let {
            if (it.asJsonPrimitive.isBoolean) it.asBoolean else it.asInt != 0
        } ?: false
        return CoreDynamicPage(items, data.text("offset").takeIf { hasMore && it.isNotBlank() && it != offset })
    }

    override suspend fun loadDetail(id: String, cookie: String): CoreDynamic {
        require(id.isNotBlank())
        val url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail".toHttpUrl().newBuilder()
            .addQueryParameter("id", id).addQueryParameter("features", "itemOpusStyle").build()
        val item = client.getBilibiliJson(url.toString(), cookie).requireBilibiliData().obj("item")
            ?: error("动态不存在或已删除")
        return parse(item, 0) ?: error("动态解析失败")
    }

    override suspend fun repost(dynamicId: String, text: String, cookie: String, csrf: String): Boolean {
        require(dynamicId.isNotBlank() && cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val body = JsonObject().apply {
            add("dyn_req", JsonObject().apply {
                add("content", JsonObject().apply {
                    add("contents", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("raw_text", text.ifBlank { "转发动态" })
                            addProperty("type", 1)
                            addProperty("biz_id", "")
                        })
                    })
                })
                addProperty("scene", 4)
            })
            add("web_repost_src", JsonObject().apply { addProperty("dyn_id_str", dynamicId) })
        }
        val json = client.postBilibiliJson(
            "https://api.bilibili.com/x/dynamic/feed/create/dyn?csrf=$csrf", cookie, body,
            referer = "https://t.bilibili.com/",
        )
        return json.get("code")?.asInt == 0
    }

    private fun parse(item: JsonObject, depth: Int): CoreDynamic? {
        if (depth > 2) return null
        val id = item.text("id_str").ifBlank { item.text("id") }
        if (id.isBlank()) return null
        val modules = item.obj("modules")
        val author = modules?.obj("module_author")
        val dynamic = modules?.obj("module_dynamic")
        val major = dynamic?.obj("major")
        val opus = major?.obj("opus")
        val archive = major?.obj("archive")
        val article = major?.obj("article")
        val images = (major?.obj("draw")?.arrayObjects("items").orEmpty().map { it.text("src") } +
            opus?.arrayObjects("pics").orEmpty().map { it.text("url") }).map(::imageUrl).filter { it.isNotBlank() }.distinct()
        val text = dynamic?.obj("desc")?.text("text").orEmpty().ifBlank {
            opus?.obj("summary")?.text("text").orEmpty().ifBlank {
                opus?.text("title").orEmpty().ifBlank { article?.text("title").orEmpty().ifBlank { major?.obj("draw")?.text("desc").orEmpty() } }
            }
        }
        val bvid = archive?.text("bvid").orEmpty()
        val video = if (bvid.isNotBlank()) CoreVideo(
            id = archive?.text("aid")?.toLongOrNull() ?: 0,
            bvid = bvid, title = archive?.text("title").orEmpty(), cover = imageUrl(archive?.text("cover").orEmpty()),
            author = author?.text("name").orEmpty(), durationText = archive?.text("duration_text").orEmpty(),
            viewText = archive?.obj("stat")?.text("play").orEmpty(),
        ) else null
        val type = major?.text("type").orEmpty()
        val supported = type in setOf("", "MAJOR_TYPE_ARCHIVE", "MAJOR_TYPE_DRAW", "MAJOR_TYPE_OPUS", "MAJOR_TYPE_ARTICLE", "MAJOR_TYPE_NONE")
        val original = item.obj("orig")?.let { parse(it, depth + 1) }
        return CoreDynamic(
            id, author?.text("name").orEmpty(), imageUrl(author?.text("face").orEmpty()),
            author?.text("pub_time").orEmpty(), text, images, video, original,
            when {
                !supported -> "暂不支持此动态卡片（$type）"
                item.text("type") == "DYNAMIC_TYPE_FORWARD" && original == null -> "原动态已删除或暂不可见"
                else -> null
            },
            articleId = article?.text("id")?.toLongOrNull(),
            opus = type == "MAJOR_TYPE_OPUS",
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.arrayObjects(key: String): List<JsonObject> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }.orEmpty()
    private fun imageUrl(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        value.startsWith("https://") -> value
        else -> ""
    }
}
