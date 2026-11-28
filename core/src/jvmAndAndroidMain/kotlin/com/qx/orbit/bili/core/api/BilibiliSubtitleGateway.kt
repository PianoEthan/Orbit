package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class BilibiliSubtitleGateway(private val client: OkHttpClient = OkHttpClient()) : SubtitleGateway {
    override suspend fun loadTracks(video: CoreVideo, cookie: String): List<CoreSubtitleTrack> {
        require(video.id > 0 && video.cid > 0)
        val url = client.signedBilibiliUrl("https://api.bilibili.com/x/player/wbi/v2", mapOf("aid" to "${video.id}", "cid" to "${video.cid}"))
        val data = client.getBilibiliJson(url, cookie).requireBilibiliData()
        return data.get("subtitle")?.takeIf { it.isJsonObject }?.asJsonObject?.get("subtitles")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { entry ->
            val item = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val source = item.text("subtitle_url").secureMediaUrl()
            val parsed = runCatching { source.toHttpUrl() }.getOrNull() ?: return@mapNotNull null
            if (!allowedSubtitleHost(parsed.host) || parsed.scheme != "https") return@mapNotNull null
            val generated = item.text("ai_status") in listOf("1", "2") || item.text("ai_type") == "1" || item.text("type") == "1"
            val label = item.text("lan_doc").ifBlank { item.text("lan") }
            CoreSubtitleTrack(item.text("id").toLongOrNull() ?: 0, item.text("lan"), label, parsed.toString(), generated)
        }.orEmpty().distinctBy { it.url }
    }

    override suspend fun loadCues(track: CoreSubtitleTrack, cookie: String): List<CoreSubtitleCue> {
        val url = track.url.toHttpUrl()
        require(url.scheme == "https" && allowedSubtitleHost(url.host)) { "不支持的字幕来源" }
        val data = client.getBilibiliJson(url.toString())
        return data.get("body")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { entry ->
            val item = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val from = item.text("from").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
            val to = item.text("to").toDoubleOrNull()?.takeIf { it.isFinite() && it > from } ?: return@mapNotNull null
            CoreSubtitleCue((from * 1000).toLong(), (to * 1000).toLong(), item.text("content"))
        }.orEmpty().sortedBy { it.fromMs }
    }

    private fun allowedSubtitleHost(host: String): Boolean = host == "hdslb.com" || host.endsWith(".hdslb.com") || host == "bilibili.com" || host.endsWith(".bilibili.com")
    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}
