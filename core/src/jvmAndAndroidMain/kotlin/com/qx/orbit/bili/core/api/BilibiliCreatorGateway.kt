package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class BilibiliCreatorGateway(private val client: OkHttpClient = OkHttpClient()) : CreatorGateway {
    override suspend fun loadCreator(mid: Long, cookie: String): CoreCreator {
        require(mid > 0)
        val data = client.getBilibiliJson("https://api.bilibili.com/x/web-interface/card?mid=$mid", cookie).requireBilibiliData()
        val card = checkNotNull(data.get("card")?.takeIf { it.isJsonObject }?.asJsonObject) { "用户资料缺失" }
        return CoreCreator(mid, card.text("name"), card.text("face").secureMediaUrl(), card.text("sign"),
            card.number("fans"), card.number("attention"), data.get("following")?.takeUnless { it.isJsonNull }?.asBoolean ?: false)
    }

    override suspend fun loadVideos(mid: Long, page: Int, cookie: String): CoreCreatorVideos {
        require(mid > 0 && page > 0)
        val url = client.signedBilibiliUrl("https://api.bilibili.com/x/space/wbi/arc/search", mapOf(
            "mid" to "$mid", "pn" to "$page", "ps" to "30", "order" to "pubdate", "tid" to "0"))
        val data = client.getBilibiliJson(url, cookie).requireBilibiliData()
        val list = data.get("list")?.takeIf { it.isJsonObject }?.asJsonObject?.get("vlist")?.takeIf { it.isJsonArray }?.asJsonArray
        val videos = list?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.let { item ->
            item.text("bvid").takeIf { value -> value.isNotBlank() }?.let { bvid ->
                CoreVideo(item.number("aid"), bvid, item.text("title"), item.text("pic").secureMediaUrl(), item.text("author"),
                    durationText = item.text("length"), viewText = item.text("play"))
            }
        } }.orEmpty()
        val total = data.get("page")?.takeIf { it.isJsonObject }?.asJsonObject?.number("count") ?: 0
        return CoreCreatorVideos(videos, (list?.size() ?: 0) > 0 && page.toLong() * 30 < total)
    }

    override suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String) {
        require(mid > 0 && action in listOf(1, 2, 5, 6))
        require(cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val result = client.postBilibiliForm("https://api.bilibili.com/x/relation/modify", cookie,
            mapOf("fid" to "$mid", "act" to "$action", "re_src" to "11", "csrf" to csrf))
        check(result.number("code") == 0L && result.has("code")) { result.text("message").ifBlank { "操作结果未确认，请刷新资料" } }
    }

    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.number(key: String): Long = get(key)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
}

internal fun String.secureMediaUrl(): String = when {
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}
