package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
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

object LiveApi {

    private val api by lazy { BiliApiService.create() }

    internal data class LiveRoomListData(
        @SerializedName("list") val list: List<LiveRoom>? = null,
        @SerializedName("rooms") val rooms: List<LiveRoom>? = null
    )

    internal data class RoomInfoData(
        @SerializedName("room_id") val room_id: Long = 0,
        @SerializedName("short_id") val short_id: Long = 0,
        @SerializedName("uid") val uid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("tags") val tags: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("online") val online: Int = 0,
        @SerializedName("attention") val attention: Int = 0,
        @SerializedName("user_cover") val user_cover: String? = null,
        @SerializedName("cover") val cover: String? = null,
        @SerializedName("keyframe") val keyframe: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("area_id") val area_id: Int = 0,
        @SerializedName("area_name") val area_name: String? = null,
        @SerializedName("area_parent_id") val area_parent_id: Int = 0,
        @SerializedName("area_parent_name") val area_parent_name: String? = null,
        @SerializedName("live_status") val live_status: Int = 0,
        @SerializedName("live_time") val liveTime: String? = null,
        @SerializedName("is_portrait") val is_portrait: Boolean = false
    )

    internal data class RoomPlayInfoData(
        @SerializedName("room_id") val room_id: Long = 0,
        @SerializedName("short_id") val short_id: Long = 0,
        @SerializedName("uid") val uid: Long = 0,
        @SerializedName("is_hidden") val isHidden: Boolean = false,
        @SerializedName("is_locked") val isLocked: Boolean = false,
        @SerializedName("is_portrait") val isPortrait: Boolean = false,
        @SerializedName("live_status") val live_status: Int = 0,
        @SerializedName("encrypted") val encrypted: Boolean = false,
        @SerializedName("pwd_verified") val pwd_verified: Boolean = false,
        @SerializedName("live_time") val live_time: Long = 0,
        @SerializedName("playurl_info") val playurl_info: PlayurlInfo? = null,
        @SerializedName("official_type") val official_type: Int = 0,
        @SerializedName("official_room_id") val official_room_id: Int = 0,
        @SerializedName("risk_with_delay") val risk_with_delay: Int = 0
    )

    suspend fun getRecommend(page: Int): List<LiveRoom> = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "page" to page.toString(),
            "page_size" to "10",
            "platform" to "web"
        ))
        when (val resp = api.getLiveRecommend(params)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<LiveRoomListData>>() {}.type
                val parsed: ApiResponse<LiveRoomListData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext emptyList()
                parsed.data.list ?: parsed.data.rooms ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun getFollowed(page: Int): List<LiveRoom> = withContext(Dispatchers.IO) {
        when (val resp = api.getLiveFollowed(page)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<LiveRoomListData>>() {}.type
                val parsed: ApiResponse<LiveRoomListData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext emptyList()
                parsed.data.list ?: parsed.data.rooms ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun getRoomInfo(roomId: Long): LiveRoom? = withContext(Dispatchers.IO) {
        when (val resp = api.getLiveRoomInfo(roomId)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<RoomInfoData>>() {}.type
                val parsed: ApiResponse<RoomInfoData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext null
                val d = parsed.data
                LiveRoom(
                    roomid = d.room_id,
                    short_id = d.short_id,
                    uid = d.uid,
                    title = d.title ?: "",
                    uname = d.uname ?: "",
                    tags = d.tags ?: "",
                    description = d.description ?: "",
                    online = d.online,
                    attention = d.attention,
                    user_cover = d.user_cover ?: "",
                    cover = d.cover ?: "",
                    keyframe = d.keyframe ?: "",
                    face = d.face?.takeIf { it.isNotEmpty() } ?: "https://i0.hdslb.com/face/${d.uid}.jpg",
                    area_parent_id = d.area_parent_id,
                    area_parent_name = d.area_parent_name ?: "",
                    area_id = d.area_id,
                    area_name = d.area_name ?: "",
                    live_status = d.live_status,
                    liveTime = d.liveTime ?: "",
                    is_portrait = d.is_portrait
                )
            }
            is Result.Error -> null
        }
    }

    suspend fun getRoomPlayInfo(roomId: Long, qn: Int): LivePlayInfo? = withContext(Dispatchers.IO) {
        val params = mapOf(
            "room_id" to roomId.toString(),
            "qn" to qn.toString(),
            "protocol" to "0,1",
            "format" to "0,1,2",
            "codec" to "0,1,2",
            "platform" to "web",
            "ptype" to "8",
            "dolby" to "5",
            "panorama" to "1"
        )
        when (val resp = api.getLivePlayInfo(params)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<RoomPlayInfoData>>() {}.type
                val parsed: ApiResponse<RoomPlayInfoData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess || parsed.data == null) return@withContext null
                val d = parsed.data
                LivePlayInfo(
                    roomid = d.room_id,
                    short_id = d.short_id,
                    uid = d.uid,
                    isHidden = d.isHidden,
                    isLocked = d.isLocked,
                    isPortrait = d.isPortrait,
                    live_status = d.live_status,
                    encrypted = d.encrypted,
                    pwd_verified = d.pwd_verified,
                    live_time = d.live_time,
                    playurl_info = d.playurl_info,
                    official_type = d.official_type,
                    official_room_id = d.official_room_id,
                    risk_with_delay = d.risk_with_delay
                )
            }
            is Result.Error -> null
        }
    }

    data class DanmuInfoData(
        @SerializedName("host_list") val host_list: List<DanmuHost>? = null,
        @SerializedName("token") val token: String? = null
    )

    data class DanmuHost(
        @SerializedName("host") val host: String? = null,
        @SerializedName("wss_port") val wss_port: Int = 0,
        @SerializedName("ws_port") val ws_port: Int = 0
    )

    suspend fun getDanmuInfo(roomId: Long): DanmuInfoData? = withContext(Dispatchers.IO) {
        val params = WbiSigner.signParams(mapOf(
            "id" to roomId.toString(),
            "type" to "0"
        ))
        when (val resp = api.getDanmuInfo(params)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<DanmuInfoData>>() {}.type
                val parsed: ApiResponse<DanmuInfoData>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data
            }
            is Result.Error -> null
        }
    }

    data class EmoticonPanelData(
        @SerializedName("data") val packages: List<LiveEmoticonPackage>? = null,
        @SerializedName("fans_brand") val fansBrand: Int = 0,
        @SerializedName("purchase_url") val purchaseUrl: String? = null
    )

    data class LiveEmoticonPackage(
        @SerializedName("pkg_id") val pkgId: Int = 0,
        @SerializedName("pkg_name") val pkgName: String = "",
        @SerializedName("pkg_type") val pkgType: Int = 0,
        @SerializedName("pkg_perm") val pkgPerm: Int = 0,
        @SerializedName("current_cover") val currentCover: String = "",
        @SerializedName("pkg_descript") val pkgDescript: String = "",
        @SerializedName("top_show") val topShow: LiveEmoticonTopShow? = null,
        @SerializedName("top_show_recent") val topShowRecent: LiveEmoticonTopShow? = null,
        @SerializedName("unlock_identity") val unlockIdentity: Int = 0,
        @SerializedName("unlock_need_gift") val unlockNeedGift: Int = 0,
        @SerializedName("emoticons") val emoticons: List<LiveEmoticon> = emptyList(),
        @SerializedName("recently_used_emoticons") val recentlyUsed: List<LiveEmoticon> = emptyList()
    )

    data class LiveEmoticonTopShow(
        @SerializedName("image") val image: String = "",
        @SerializedName("text") val text: String = ""
    )

    data class LiveEmoticon(
        @SerializedName("bulge_display") val bulgeDisplay: Int = 0,
        @SerializedName("descript") val descript: String = "",
        @SerializedName("emoji") val emoji: String = "",
        @SerializedName("emoticon_id") val emoticonId: Long = 0,
        @SerializedName("emoticon_unique") val emoticonUnique: String = "",
        @SerializedName("emoticon_value_type") val emoticonValueType: Int = 0,
        @SerializedName("height") val height: Int = 0,
        @SerializedName("identity") val identity: Int = 0,
        @SerializedName("in_player_area") val inPlayerArea: Int = 0,
        @SerializedName("is_dynamic") val isDynamic: Int = 0,
        @SerializedName("perm") val perm: Int = 0,
        @SerializedName("unlock_need_gift") val unlockNeedGift: Int = 0,
        @SerializedName("unlock_need_level") val unlockNeedLevel: Int = 0,
        @SerializedName("unlock_show_color") val unlockShowColor: String = "",
        @SerializedName("unlock_show_image") val unlockShowImage: String = "",
        @SerializedName("unlock_show_text") val unlockShowText: String = "",
        @SerializedName("url") val url: String = "",
        @SerializedName("width") val width: Int = 0
    )

    suspend fun getLiveEmoticons(roomId: Long, platform: String = "pc"): List<LiveEmoticonPackage> = withContext(Dispatchers.IO) {
        when (val resp = api.getLiveEmoticons(platform, roomId)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<EmoticonPanelData>>() {}.type
                val parsed: ApiResponse<EmoticonPanelData>? = GsonConfig.gson.fromJson(resp.data, type)
                if (parsed == null || !parsed.isSuccess) return@withContext emptyList()
                parsed?.data?.packages ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    data class SendResult(
        val ok: Boolean,
        val message: String
    )

    private suspend fun doSend(body: FormBody, roomId: Long): SendResult = withContext(Dispatchers.IO) {
        try {
            val signedUrl = WbiSigner.signUrl("https://api.live.bilibili.com/msg/send?web_location=444.8")
            val request = Request.Builder()
                .url(signedUrl)
                .post(body)
                .addHeader("Cookie", CookieManager.getCookie())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                .addHeader("Referer", "https://live.bilibili.com/$roomId")
                .build()
            HttpClient.client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                android.util.Log.d("LiveApi", "sendMsg response: $respBody")
                val code = Regex("\"code\"\\s*:\\s*(\\d+)").find(respBody)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val msg = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(respBody)?.groupValues?.get(1).orEmpty()
                SendResult(ok = code == 0, message = msg)
            }
        } catch (e: Exception) {
            android.util.Log.e("LiveApi", "sendMsg error", e)
            SendResult(ok = false, message = e.message ?: "unknown")
        }
    }

    suspend fun sendDanmaku(text: String, roomId: Long): SendResult = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val rnd = (System.currentTimeMillis() / 1000).toString()
        val body = FormBody.Builder()
            .add("bubble", "0")
            .add("msg", text)
            .add("color", "16777215")
            .add("mode", "1")
            .add("room_type", "0")
            .add("jumpfrom", "0")
            .add("reply_mid", "0")
            .add("reply_attr", "0")
            .add("replay_dmid", "")
            .add("statistics", "{\"appId\":100,\"platform\":5}")
            .add("reply_type", "0")
            .add("reply_uname", "")
            .add("fontsize", "25")
            .add("rnd", rnd)
            .add("roomid", roomId.toString())
            .add("csrf", csrf)
            .add("csrf_token", csrf)
            .build()
        doSend(body, roomId)
    }

    suspend fun sendLiveEmote(emoticonUnique: String, roomId: Long): SendResult = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val rnd = (System.currentTimeMillis() / 1000).toString()
        val body = FormBody.Builder()
            .add("bubble", "0")
            .add("msg", emoticonUnique)
            .add("color", "16777215")
            .add("mode", "1")
            .add("dm_type", "1")
            .add("emoticonOptions", "[object Object]")
            .add("fontsize", "25")
            .add("rnd", rnd)
            .add("roomid", roomId.toString())
            .add("csrf", csrf)
            .add("csrf_token", csrf)
            .build()
        doSend(body, roomId)
    }
}
