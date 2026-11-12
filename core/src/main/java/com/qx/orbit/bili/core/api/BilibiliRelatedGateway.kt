package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.OkHttpClient

fun interface RelatedGateway {
    suspend fun loadRelated(bvid: String, cookie: String): List<CoreVideo>
}

class BilibiliRelatedGateway(private val client: OkHttpClient = OkHttpClient()) : RelatedGateway {
    override suspend fun loadRelated(bvid: String, cookie: String): List<CoreVideo> {
        require(bvid.isNotBlank())
        val response = client.getBilibiliJson("https://api.bilibili.com/x/web-interface/archive/related?bvid=$bvid", cookie)
        check(response.get("code")?.asInt == 0) { response.get("message")?.asString ?: "相关推荐加载失败" }
        return response.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
            it.takeIf { value -> value.isJsonObject }?.asJsonObject?.coreArchiveVideo()
        }.orEmpty().distinctBy { it.bvid }
    }
}

internal fun JsonObject.coreArchiveVideo(): CoreVideo? {
    fun text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    val bvid = text("bvid").takeIf { it.isNotBlank() } ?: return null
    val owner = get("owner")?.takeIf { it.isJsonObject }?.asJsonObject
    val duration = text("duration").toLongOrNull() ?: 0
    val views = get("stat")?.takeIf { it.isJsonObject }?.asJsonObject?.get("view")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    return CoreVideo(text("aid").toLongOrNull() ?: text("id").toLongOrNull() ?: 0L, bvid, text("title"),
        text("pic").secureMediaUrl(), owner?.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        cid = text("cid").toLongOrNull() ?: 0L, durationText = if (duration > 0) "%d:%02d".format(duration / 60, duration % 60) else "", viewText = views)
}
