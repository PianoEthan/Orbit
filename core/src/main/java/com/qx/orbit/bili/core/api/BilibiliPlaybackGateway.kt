package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.OkHttpClient

interface PlaybackGateway {
    suspend fun resolve(video: CoreVideo): CorePlayback
    suspend fun resolveQuality(video: CoreVideo, quality: Int): CorePlayback = resolve(video)
}

data class CorePlaybackQuality(val id: Int, val label: String)

data class CorePlayback(
    val video: CoreVideo,
    val urls: List<String>,
    val quality: Int = 32,
    val qualities: List<CorePlaybackQuality> = emptyList(),
    val audioUrl: String? = null,
    val videoBackups: List<String> = emptyList(),
    val audioBackups: List<String> = emptyList(),
    val segmentBackups: List<List<String>> = emptyList(),
    val segmentDurationsMs: List<Long> = emptyList(),
)

class BilibiliPlaybackGateway(
    private val client: OkHttpClient = OkHttpClient(),
    private val details: VideoGateway = BilibiliVideoGateway(client),
    private val cookieProvider: () -> String = { "" },
) : PlaybackGateway {
    override suspend fun resolve(video: CoreVideo): CorePlayback = resolveQuality(video, 32)

    override suspend fun resolveQuality(video: CoreVideo, quality: Int): CorePlayback {
        require(quality > 0)
        val resolved = if (video.cid > 0) video else details.loadDetails(video).video
        require(resolved.cid > 0) { "缺少视频分集信息" }
        val url = client.signedBilibiliUrl("https://api.bilibili.com/x/player/wbi/playurl", mapOf(
            "bvid" to resolved.bvid,
            "cid" to resolved.cid.toString(),
            "qn" to quality.toString(),
            "fnval" to "144",
            "fnver" to "0",
            "fourk" to "1",
            "high_quality" to "1",
            "gaia_source" to "pre-load",
        ))
        val data = client.getBilibiliJson(url, cookie = cookieProvider()).requireBilibiliData()
        val descriptions = data.get("accept_description")?.takeIf { it.isJsonArray }?.asJsonArray
        val qualities = data.get("accept_quality")?.takeIf { it.isJsonArray }?.asJsonArray?.mapIndexed { index, entry ->
            val id = entry.asInt
            val label = descriptions?.takeIf { index < it.size() }?.get(index)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            CorePlaybackQuality(id, label.ifBlank { "画质 $id" })
        }.orEmpty().distinctBy { it.id }
        val dash = data.get("dash")?.takeIf { it.isJsonObject }?.asJsonObject
        val videoStreams = dash?.objects("video").orEmpty().filter {
            it.mediaUrl().isNotBlank() && (it.text("codecs").startsWith("avc") || it.get("codecid")?.asInt == 7)
        }
        val selectedId = videoStreams.mapNotNull { it.get("id")?.asInt }.let { ids ->
            ids.filter { it <= quality }.maxOrNull() ?: ids.minOrNull()
        }
        val selected = videoStreams.filter { it.get("id")?.asInt == selectedId }.maxByOrNull { it.bandwidth() }
        val audio = dash?.objects("audio").orEmpty().filter {
            it.mediaUrl().isNotBlank() && (it.text("codecs").startsWith("mp4a") || it.text("mimeType") == "audio/mp4" || it.text("mime_type") == "audio/mp4")
        }.maxByOrNull { it.bandwidth() }
        val urls: List<String>
        val videoBackups: List<String>
        val audioUrl: String?
        val audioBackups: List<String>
        val actualQuality: Int
        val segmentBackups: List<List<String>>
        if (selected != null) {
            check(audio != null) { "未获得兼容的音频流，暂不能播放该 DASH 视频" }
            urls = listOf(selected.mediaUrl())
            videoBackups = selected.backupUrls()
            segmentBackups = emptyList()
            audioUrl = audio.mediaUrl()
            audioBackups = audio.backupUrls()
            actualQuality = selectedId ?: quality
        } else {
            val progressive = data.objects("durl")
            urls = progressive.map { normalizeUrl(it.text("url")) }
            segmentBackups = progressive.map { item -> item.urls("backup_url", "backupUrl").map(::normalizeUrl).filter { it.startsWith("https://") }.distinct() }
            videoBackups = segmentBackups.firstOrNull().orEmpty()
            audioUrl = null
            audioBackups = emptyList()
            actualQuality = data.get("quality")?.takeUnless { it.isJsonNull }?.asInt ?: quality
        }
        check(urls.isNotEmpty() && urls.all { it.startsWith("https://") }) {
            "未获得兼容的播放流，该视频可能需要登录或设备暂不支持当前编码"
        }
        val available = if (qualities.any { it.id == actualQuality }) qualities
            else qualities + CorePlaybackQuality(actualQuality, "画质 $actualQuality")
        val durations = if (audioUrl == null) data.objects("durl").map { it.get("length")?.takeUnless { value -> value.isJsonNull }?.asLong ?: 0L } else emptyList()
        return CorePlayback(resolved, urls, actualQuality, available, audioUrl, videoBackups, audioBackups, segmentBackups, durations)
    }

    private fun JsonObject.objects(key: String): List<JsonObject> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.bandwidth(): Long = get("bandwidth")?.takeUnless { it.isJsonNull }?.asLong ?: 0
    private fun JsonObject.mediaUrl(): String = normalizeUrl(text("baseUrl").ifBlank { text("base_url") }).takeIf { it.startsWith("https://") }.orEmpty()
    private fun JsonObject.urls(vararg names: String): List<String> = names.asSequence().flatMap { name ->
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive }?.asString }.orEmpty().asSequence()
    }.toList()
    private fun JsonObject.backupUrls(): List<String> = urls("backup_url", "backupUrl").map(::normalizeUrl).filter { it.startsWith("https://") }
    private fun normalizeUrl(url: String): String = when {
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        url.startsWith("//") -> "https:$url"
        else -> url
    }
}
