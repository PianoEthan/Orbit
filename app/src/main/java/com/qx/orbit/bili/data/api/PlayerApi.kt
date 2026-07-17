package com.qx.orbit.bili.data.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.qx.orbit.bili.presentation.ui.components.RoundToast
import com.qx.orbit.bili.util.AccountUtil
import com.qx.orbit.bili.util.PlayerConfig
import com.qx.orbit.bili.util.SharedPreferencesUtil
import java.io.Serializable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.HashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        @SerializedName("subtitle") val subtitle: SubtitleWrapper? = null
    )

    internal data class SubtitleWrapper(
        @SerializedName("subtitles") val subtitles: List<SubtitleDataInner>? = null
    )

    internal data class SubtitleDataInner(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("lan") val lan: String? = null,
        @SerializedName("lan_doc") val lan_doc: String? = null,
        @SerializedName("subtitle_url") val subtitle_url: String? = null,
        @SerializedName("ai_status") val ai_status: Int = 0,
        @SerializedName("ai_type") val ai_type: Int = 0,
        @SerializedName("type") val type: Int = 0
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

    internal data class PlayerV2Data(
        @SerializedName("last_play_time") val last_play_time: Long = 0,
        @SerializedName("last_play_cid") val last_play_cid: Long = 0
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
        val isLoggedIn = AccountUtil.isUserLoggedIn()
        val tryLookEnabled = PlayerConfig.isTryLookEnabled()
        val tryLook = !isLoggedIn && tryLookEnabled
        
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
            "isGaiaAvoided" to "true",
            "try_look" to if (tryLook) "1" else "0"
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
            val selectedAudio = if (playerData.audioQn != -1) dashData.getAudioStream(playerData.audioQn) else dashData.getBestAudioStream()
            playerData.copy(
                dashData = dashData,
                videoUrl = selectedVideo?.baseUrl ?: (videoStreams.firstOrNull { it.codecid == 7 } ?: videoStreams.firstOrNull())?.baseUrl ?: "",
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
        val isLoggedIn = AccountUtil.isUserLoggedIn()
        val tryLookEnabled = PlayerConfig.isTryLookEnabled()
        val tryLook = !isLoggedIn && tryLookEnabled
        
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
            "isGaiaAvoided" to "true",
            "try_look" to if (tryLook) "1" else "0"
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
            qnStrList = data.accept_description?.toTypedArray(),
            qnValueList = data.accept_quality?.toIntArray()
        )
    }

    suspend fun getBangumi(playerData: PlayerData, forceMp4: Boolean = false): PlayerData = withContext(Dispatchers.IO) {
        val isLoggedIn = AccountUtil.isUserLoggedIn()
        val tryLookEnabled = PlayerConfig.isTryLookEnabled()
        val tryLook = !isLoggedIn && tryLookEnabled
        
        val fnval = if (forceMp4) "1" else "4048"
        val url = "https://api.bilibili.com/pgc/player/web/playurl?avid=${playerData.aid}&cid=${playerData.cid}&qn=${playerData.qn}&fnval=$fnval&fourk=1&try_look=${if (tryLook) "1" else "0"}"
        val json = httpGet(url)
        val type = object : TypeToken<ApiResponse<PlayUrlData>>() {}.type
        val resp: ApiResponse<PlayUrlData>? = GsonConfig.gson.fromJson(json, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext playerData
        val data = resp.data
        if (forceMp4) {
            val videoUrl = data.durl?.firstOrNull()?.url ?: ""
            return@withContext playerData.copy(
                videoUrl = videoUrl,
                qnStrList = data.accept_description?.toTypedArray(),
                qnValueList = data.accept_quality?.toIntArray()
            )
        }
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
            val selectedAudio = if (playerData.audioQn != -1) dashData.getAudioStream(playerData.audioQn) else dashData.getBestAudioStream()
            playerData.copy(
                dashData = dashData,
                videoUrl = selectedVideo?.baseUrl ?: (videoStreams.firstOrNull { it.codecid == 7 } ?: videoStreams.firstOrNull())?.baseUrl ?: "",
                audioUrl = selectedAudio?.baseUrl ?: audioStreams.firstOrNull()?.baseUrl ?: "",
                qnStrList = data.accept_description?.toTypedArray(),
                qnValueList = data.accept_quality?.toIntArray()
            )
        } else {
            val videoUrl = data.durl?.firstOrNull()?.url ?: ""
            playerData.copy(
                videoUrl = videoUrl,
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
        resp.data.subtitle?.subtitles?.map { s ->
            val isAI = s.ai_status == 1 || s.ai_status == 2 || s.ai_type == 1 || s.type == 1
            val baseLang = s.lan_doc ?: s.lan ?: ""
            val displayLang = if (isAI && !baseLang.contains("AI", ignoreCase = true)) "$baseLang (AI)" else baseLang
            SubtitleLink(
                id = s.id,
                isAI = isAI,
                lang = displayLang,
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

    suspend fun getHistoryProgress(aid: Long, cid: Long): History? = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getPlayerV2(aid, cid)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext null
        }
        val type = object : TypeToken<ApiResponse<PlayerV2Data>>() {}.type
        val resp: ApiResponse<PlayerV2Data>? = GsonConfig.gson.fromJson(jsonElement, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext null
        val data = resp.data
        if (data.last_play_time > 0) {
            History(
                cid = data.last_play_cid,
                progress = (data.last_play_time / 1000).toInt()
            )
        } else null
    }

    suspend fun getSubtitle(url: String): Array<Subtitle> = withContext(Dispatchers.IO) {
        val fullUrl = normalizeUrl(url)
        val json = httpGet(fullUrl)
        parseSubtitleBody(json)
    }

    suspend fun getSubtitleRaw(url: String): String = withContext(Dispatchers.IO) {
        val fullUrl = normalizeUrl(url)
        httpGet(fullUrl)
    }

    private fun normalizeUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "file://$url"
        else -> "https://$url"
    }

    private fun parseSubtitleBody(json: String): Array<Subtitle> {
        val body: SubtitleBody? = GsonConfig.gson.fromJson(json, SubtitleBody::class.java)
        return body?.body?.map { s ->
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

    fun jumpToPlayer(context: Context, navController: NavController, playerData: PlayerData, forcePlayerChoice: String? = null) {
        val playerChoice = forcePlayerChoice ?: SharedPreferencesUtil.getString("player", "apsisPlayer")
        when (playerChoice) {
            "aliangPlayer" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val pd = when (playerData.type) {
                            PlayerData.TYPE_VIDEO -> getVideo(playerData)
                            PlayerData.TYPE_BANGUMI -> getBangumi(playerData)
                            else -> playerData
                        }
                        withContext(Dispatchers.Main) {
                            val intent = Intent().apply {
                                setClassName("com.aliangmaker.media", "com.aliangmaker.media.PlayVideoActivity")
                                putExtra("name", pd.title)
                                putExtra("danmaku", "https://comment.bilibili.com/${pd.cid}.xml")
                                putExtra("live_mode", pd.type == PlayerData.TYPE_LIVE)
                                data = Uri.parse(pd.videoUrl)
                                if (pd.type != PlayerData.TYPE_LOCAL) {
                                    val headers = HashMap<String, String>().apply {
                                        put("Cookie", CookieManager.getCookie())
                                        put("Referer", "https://www.bilibili.com/")
                                    }
                                    putExtra("cookie", headers as Serializable)
                                    putExtra("agent", USER_AGENT)
                                    putExtra("progress", pd.progress * 1000L)
                                }
                                action = Intent.ACTION_VIEW
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                RoundToast.show(context, "未找到凉腕播放器或启动失败")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            RoundToast.show(context, "获取播放地址失败: ${e.message}")
                        }
                    }
                }
            }
            "pureAudioPlayer" -> {
                val pd = playerData.copy(audioUrl = "audio")
                val jsonStr = GsonConfig.gson.toJson(pd)
                val encodedJson = URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.toString())
                navController.navigate("player/$encodedJson")
            }
            else -> { // apsisPlayer
                val jsonStr = GsonConfig.gson.toJson(playerData)
                val encodedJson = URLEncoder.encode(jsonStr, StandardCharsets.UTF_8.toString())
                navController.navigate("player/$encodedJson")
            }
        }
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
