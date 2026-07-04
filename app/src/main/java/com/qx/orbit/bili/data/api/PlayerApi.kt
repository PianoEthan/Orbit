package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object PlayerApi {

    private val api by lazy { BiliApiService.create() }

    internal data class PlayUrlData(
        @SerializedName("quality") val quality: Int = 0,
        @SerializedName("timelength") val timelength: Int = 0,
        @SerializedName("dash") val dash: DashDataResponse? = null,
        @SerializedName("durl") val durl: List<DurlItem>? = null,
        @SerializedName("accept_quality") val accept_quality: List<Int>? = null,
        @SerializedName("accept_description") val accept_description: List<String>? = null,
        @SerializedName("video_project") val video_project: Boolean = false
    )

    internal data class DashDataResponse(
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("minBufferTime") val minBufferTime: Double = 0.0,
        @SerializedName("video") val video: List<DashVideoItem>? = null,
        @SerializedName("audio") val audio: List<DashAudioItem>? = null,
        @SerializedName("dolby") val dolby: DolbyData? = null,
        @SerializedName("flac") val flac: FlacData? = null
    )

    internal data class DashVideoItem(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("baseUrl") val baseUrl: String? = null,
        @SerializedName("base_url") val base_url: String? = null,
        @SerializedName("backupUrl") val backupUrl: List<String>? = null,
        @SerializedName("backup_url") val backup_url: List<String>? = null,
        @SerializedName("bandwidth") val bandwidth: Long = 0,
        @SerializedName("mimeType") val mimeType: String? = null,
        @SerializedName("codecs") val codecs: String? = null,
        @SerializedName("width") val width: Int = 0,
        @SerializedName("height") val height: Int = 0,
        @SerializedName("frameRate") val frameRate: String? = null,
        @SerializedName("codecid") val codecid: Int = 0
    )

    internal data class DashAudioItem(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("baseUrl") val baseUrl: String? = null,
        @SerializedName("base_url") val base_url: String? = null,
        @SerializedName("backupUrl") val backupUrl: List<String>? = null,
        @SerializedName("backup_url") val backup_url: List<String>? = null,
        @SerializedName("bandwidth") val bandwidth: Long = 0,
        @SerializedName("mimeType") val mimeType: String? = null,
        @SerializedName("codecs") val codecs: String? = null,
        @SerializedName("codecid") val codecid: Int = 0
    )

    internal data class DolbyData(
        @SerializedName("audio") val audio: List<DashAudioItem>? = null
    )

    internal data class FlacData(
        @SerializedName("audio") val audio: DashAudioItem? = null
    )

    internal data class DurlItem(
        @SerializedName("url") val url: String? = null,
        @SerializedName("length") val length: Long = 0,
        @SerializedName("size") val size: Long = 0
    )

    internal data class SubtitleLinkData(
        @SerializedName("subtitles") val subtitles: List<SubtitleDataInner>? = null
    )

    internal data class SubtitleDataInner(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("lan") val lan: String? = null,
        @SerializedName("lan_doc") val lan_doc: String? = null,
        @SerializedName("subtitle_url") val subtitle_url: String? = null,
        @SerializedName("ai_status") val ai_status: Int = 0
    )

    internal data class ViewPointData(
        @SerializedName("view_points") val view_points: List<SubtitleItem>? = null
    )

    internal data class SubtitleItem(
        @SerializedName("type") val type: Int = 0,
        @SerializedName("from") val from: Int = 0,
        @SerializedName("to") val to: Int = 0,
        @SerializedName("url") val url: String? = null,
        @SerializedName("imgUrl") val imgUrl: String? = null,
        @SerializedName("logoUrl") val logoUrl: String? = null,
        @SerializedName("content") val content: String? = null
    )

    internal data class SubtitleInner(
        @SerializedName("interaction") val interaction: InteractionData? = null
    )

    internal data class InteractionData(
        @SerializedName("graph_version") val graph_version: Long = 0
    )

    internal data class SubtitleBody(
        @SerializedName("body") val body: List<SubtitleEntry>? = null
    )

    internal data class SubtitleEntry(
        @SerializedName("from") val from: Double = 0.0,
        @SerializedName("to") val to: Double = 0.0,
        @SerializedName("content") val content: String? = null
    )

    internal data class HighEnergyResponse(
        @SerializedName("stepSec") val stepSec: Int = 0,
        @SerializedName("tag") val tag: String? = null,
        @SerializedName("events") val events: String? = null
    )

    suspend fun getVideoDash(playerData: PlayerData): PlayerData = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "avid" to playerData.aid.toString(),
            "cid" to playerData.cid.toString(),
            "qn" to playerData.qn.toString(),
            "fnval" to "16",
            "fourk" to "1",
            "fnver" to "0",
            "platform" to "pc",
            "voice_balance" to "1",
            "gaia_source" to "pre-load",
            "isGaiaAvoided" to "true"
        ))
        val jsonElement = when (val result = api.getPlayUrl(params)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext playerData
        }
        val type = object : TypeToken<ApiResponse<PlayUrlData>>() {}.type
        val resp: ApiResponse<PlayUrlData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext playerData
        val data = resp.data
        val dash = data.dash
        if (dash != null) {
            val videoStreams = dash.video?.map { v ->
                DashVideoStream(
                    id = v.id,
                    baseUrl = v.baseUrl ?: v.base_url ?: "",
                    backupUrl = v.backupUrl ?: v.backup_url ?: emptyList(),
                    bandwidth = v.bandwidth,
                    mimeType = v.mimeType ?: "",
                    codecs = v.codecs ?: "",
                    width = v.width,
                    height = v.height,
                    frameRate = v.frameRate ?: "",
                    codecid = v.codecid
                )
            } ?: emptyList()
            val audioStreams = dash.audio?.map { a ->
                DashAudioStream(
                    id = a.id,
                    baseUrl = a.baseUrl ?: a.base_url ?: "",
                    backupUrl = a.backupUrl ?: a.backup_url ?: emptyList(),
                    bandwidth = a.bandwidth,
                    mimeType = a.mimeType ?: "",
                    codecs = a.codecs ?: "",
                    codecid = a.codecid
                )
            } ?: emptyList()
            val dolbyAudio = dash.dolby?.audio?.firstOrNull()?.let { a ->
                DashAudioStream(
                    id = a.id, baseUrl = a.baseUrl ?: a.base_url ?: "",
                    backupUrl = a.backupUrl ?: a.backup_url ?: emptyList(),
                    bandwidth = a.bandwidth, mimeType = a.mimeType ?: "",
                    codecs = a.codecs ?: "", codecid = a.codecid
                )
            }
            val flacAudio = dash.flac?.audio?.let { a ->
                DashAudioStream(
                    id = a.id, baseUrl = a.baseUrl ?: a.base_url ?: "",
                    backupUrl = a.backupUrl ?: a.backup_url ?: emptyList(),
                    bandwidth = a.bandwidth, mimeType = a.mimeType ?: "",
                    codecs = a.codecs ?: "", codecid = a.codecid
                )
            }
            val dashData = DashData(
                duration = dash.duration,
                minBufferTime = dash.minBufferTime,
                videoStreams = videoStreams,
                audioStreams = audioStreams,
                dolbyAudio = dolbyAudio,
                flacAudio = flacAudio
            )
            val selectedVideo = dashData.getVideoStream(playerData.qn)
            val selectedAudio = dashData.getBestAudioStream()
            playerData.copy(
                dashData = dashData,
                videoUrl = selectedVideo?.baseUrl ?: videoStreams.firstOrNull()?.baseUrl ?: "",
                audioUrl = selectedAudio?.baseUrl ?: audioStreams.firstOrNull()?.baseUrl ?: "",
                progress = data.quality,
                qnStrList = data.accept_description?.toTypedArray(),
                qnValueList = data.accept_quality?.toIntArray()
            )
        } else {
            getVideo(playerData)
        }
    }

    suspend fun getVideo(playerData: PlayerData): PlayerData = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "avid" to playerData.aid.toString(),
            "cid" to playerData.cid.toString(),
            "qn" to playerData.qn.toString(),
            "high_quality" to "1",
            "fnval" to "1",
            "fnver" to "0",
            "platform" to "html5",
            "voice_balance" to "1",
            "gaia_source" to "pre-load",
            "isGaiaAvoided" to "true"
        ))
        val jsonElement = when (val result = api.getPlayUrl(params)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext playerData
        }
        val type = object : TypeToken<ApiResponse<PlayUrlData>>() {}.type
        val resp: ApiResponse<PlayUrlData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext playerData
        val data = resp.data
        val videoUrl = data.durl?.firstOrNull()?.url ?: ""
        playerData.copy(
            videoUrl = videoUrl,
            progress = data.quality,
            qnStrList = data.accept_description?.toTypedArray(),
            qnValueList = data.accept_quality?.toIntArray()
        )
    }

    suspend fun getBangumi(playerData: PlayerData): PlayerData = withContext(Dispatchers.IO) {
        val url = "https://api.bilibili.com/pgc/player/web/playurl?avid=${playerData.aid}&cid=${playerData.cid}&qn=${playerData.qn}&fnval=1"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<PlayUrlData>>() {}.type
        val resp: ApiResponse<PlayUrlData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext playerData
        val data = resp.data
        val dash = data.dash
        if (dash != null) {
            val videoStreams = dash.video?.map { v ->
                DashVideoStream(
                    id = v.id,
                    baseUrl = v.baseUrl ?: v.base_url ?: "",
                    backupUrl = v.backupUrl ?: v.backup_url ?: emptyList(),
                    bandwidth = v.bandwidth,
                    mimeType = v.mimeType ?: "",
                    codecs = v.codecs ?: "",
                    width = v.width, height = v.height,
                    frameRate = v.frameRate ?: "",
                    codecid = v.codecid
                )
            } ?: emptyList()
            val audioStreams = dash.audio?.map { a ->
                DashAudioStream(
                    id = a.id,
                    baseUrl = a.baseUrl ?: a.base_url ?: "",
                    backupUrl = a.backupUrl ?: a.backup_url ?: emptyList(),
                    bandwidth = a.bandwidth,
                    mimeType = a.mimeType ?: "",
                    codecs = a.codecs ?: "",
                    codecid = a.codecid
                )
            } ?: emptyList()
            val dashData = DashData(
                duration = dash.duration,
                minBufferTime = dash.minBufferTime,
                videoStreams = videoStreams,
                audioStreams = audioStreams
            )
            playerData.copy(
                dashData = dashData,
                videoUrl = videoStreams.firstOrNull()?.baseUrl ?: "",
                audioUrl = audioStreams.firstOrNull()?.baseUrl ?: "",
                progress = data.quality,
                qnStrList = data.accept_description?.toTypedArray(),
                qnValueList = data.accept_quality?.toIntArray()
            )
        } else {
            val videoUrl = data.durl?.firstOrNull()?.url ?: ""
            playerData.copy(
                videoUrl = videoUrl,
                progress = data.quality,
                qnStrList = data.accept_description?.toTypedArray(),
                qnValueList = data.accept_quality?.toIntArray()
            )
        }
    }

    suspend fun getSubtitleLinks(aid: Long, cid: Long): Array<SubtitleLink> = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getPlayerV2(aid, cid)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext emptyArray()
        }
        val type = object : TypeToken<ApiResponse<SubtitleLinkData>>() {}.type
        val resp: ApiResponse<SubtitleLinkData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyArray()
        resp.data.subtitles?.map { s ->
            SubtitleLink(
                id = s.id,
                isAI = s.ai_status == 1,
                lang = s.lan_doc ?: s.lan ?: "",
                url = s.subtitle_url ?: ""
            )
        }?.toTypedArray() ?: emptyArray()
    }

    suspend fun getViewPoints(aid: Long, cid: Long): List<ViewPoint> = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getPlayerV2(aid, cid)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext emptyList()
        }
        val type = object : TypeToken<ApiResponse<ViewPointData>>() {}.type
        val resp: ApiResponse<ViewPointData>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        resp.data.view_points?.map { vp ->
            ViewPoint(
                content = vp.content ?: "",
                from = vp.from,
                to = vp.to,
                type = vp.type,
                imgUrl = vp.imgUrl ?: "",
                logoUrl = vp.logoUrl ?: ""
            )
        } ?: emptyList()
    }

    suspend fun getInteractionGraphVersion(aid: Long, cid: Long): Long = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getPlayerV2(aid, cid)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext 0L
        }
        val type = object : TypeToken<ApiResponse<SubtitleInner>>() {}.type
        val resp: ApiResponse<SubtitleInner>? = GsonConfig.gson.fromJson(jsonElement, type)
        resp?.data?.interaction?.graph_version ?: 0L
    }

    suspend fun getSubtitle(url: String): Array<Subtitle> = withContext(Dispatchers.IO) {
        val fullUrl = if (url.startsWith("http")) url else "https:$url"
        val json = httpGet(fullUrl)
        val body: SubtitleBody? = GsonConfig.gson.fromJson(json, SubtitleBody::class.java)
        body?.body?.map { s ->
            Subtitle(content = s.content ?: "", from = s.from, to = s.to)
        }?.toTypedArray() ?: emptyArray()
    }

    suspend fun getHighEnergyData(cid: Long, aid: Long): HighEnergyData? = withContext(Dispatchers.IO) {
        val url = "https://bvc.bilivideo.com/pbp/data?cid=$cid&aid=$aid"
        val json = httpGet(url)
        val resp: HighEnergyResponse? = GsonConfig.gson.fromJson(json, HighEnergyResponse::class.java)
        if (resp == null) return@withContext null
        val events = resp.events?.let { str ->
            try {
                val arr = GsonConfig.gson.fromJson(str, FloatArray::class.java)
                arr ?: floatArrayOf()
            } catch (_: Exception) { floatArrayOf() }
        } ?: floatArrayOf()
        HighEnergyData(
            stepSec = resp.stepSec,
            tagStr = resp.tag ?: "",
            events = events
        )
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
