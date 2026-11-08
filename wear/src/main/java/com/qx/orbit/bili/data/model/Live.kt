package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LiveRoom(
    @SerializedName(value = "roomid", alternate = ["room_id"]) val roomid: Long = 0,
    val short_id: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val tags: String = "",
    val description: String = "",
    val online: Int = 0,
    val attention: Int = 0,
    val user_cover: String = "",
    val cover: String = "",
    val keyframe: String = "",
    val face: String = "",
    @SerializedName(value = "area_parent_id", alternate = ["area_v2_parent_id", "parent_area_id"]) val area_parent_id: Int = 0,
    @SerializedName(value = "area_parent_name", alternate = ["area_v2_parent_name", "parent_area_name"]) val area_parent_name: String = "",
    @SerializedName(value = "area_id", alternate = ["area_v2_id"]) val area_id: Int = 0,
    @SerializedName(value = "area_name", alternate = ["area_v2_name"]) val area_name: String = "",
    val live_status: Int = 0,
    val liveTime: String = "",
    val is_portrait: Boolean = false
) : Parcelable

data class LivePlayInfo(
    @SerializedName("room_id") val roomid: Long = 0,
    val short_id: Long = 0,
    val uid: Long = 0,
    @SerializedName("is_hidden") val isHidden: Boolean = false,
    @SerializedName("is_locked") val isLocked: Boolean = false,
    @SerializedName("is_portrait") val isPortrait: Boolean = false,
    val live_status: Int = 0,
    val encrypted: Boolean = false,
    val pwd_verified: Boolean = false,
    val live_time: Long = 0,
    val playurl_info: PlayurlInfo? = null,
    val official_type: Int = 0,
    val official_room_id: Int = 0,
    val risk_with_delay: Int = 0
)

data class PlayurlInfo(
    val playurl: Playurl? = null
)

data class Playurl(
    val cid: Long = 0,
    @SerializedName("g_qn_desc") val qnDesc: List<QnDesc> = emptyList(),
    val stream: List<ProtocolInfo> = emptyList()
)

data class QnDesc(
    val qn: Int = 0,
    val desc: String = "",
    val hdr_desc: String = ""
)

data class ProtocolInfo(
    val protocol_name: String = "",
    val format: List<FormatInfo> = emptyList()
)

data class FormatInfo(
    val format_name: String = "",
    val codec: List<CodecInfo> = emptyList(),
    val master_url: String = ""
)

data class CodecInfo(
    val codec_name: String = "",
    val current_qn: Int = 0,
    @SerializedName("accept_qn") val acceptQn: List<Int> = emptyList(),
    val base_url: String = "",
    @SerializedName("url_info") val urlInfo: List<UrlInfo> = emptyList()
)

data class UrlInfo(
    val host: String = "",
    val extra: String = "",
    val stream_ttl: Int = 0
)

data class LiveInfo(
    val userInfo: UserInfo? = null,
    val liveRoom: LiveRoom? = null,
    val livePlayInfo: LivePlayInfo? = null
)
