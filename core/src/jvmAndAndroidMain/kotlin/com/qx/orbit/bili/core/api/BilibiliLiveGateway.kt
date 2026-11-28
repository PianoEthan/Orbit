package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import java.net.URLEncoder
import okhttp3.OkHttpClient

class BilibiliLiveGateway(private val client: OkHttpClient = OkHttpClient()) : LiveGateway {

    override suspend fun loadRoomInfo(roomId: Long): CoreLiveRoom {
        val data = client.getBilibiliJson("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId")
            .requireBilibiliData()
        return parseRoom(data)
    }

    override suspend fun loadStreamInfo(roomId: Long, qn: Int, cookie: String): CoreLiveStream {
        val query = mapOf(
            "room_id" to roomId.toString(),
            "qn" to qn.toString(),
            "protocol" to "0,1",
            "format" to "0,1,2",
            "codec" to "0,1,2",
            "platform" to "web",
            "ptype" to "8",
            "dolby" to "5",
            "panorama" to "1",
        ).entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val data = client
            .getBilibiliJson("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?$query", cookie)
            .requireBilibiliData()
        val playurl = data.objectValue("playurl_info")?.objectValue("playurl")
        val labels = playurl?.arrayValue("g_qn_desc")
            ?.mapNotNull { it.asJsonObjectOrNull() }
            ?.mapNotNull { entry -> entry.intValue("qn").takeIf { it > 0 }?.let { it to entry.text("desc") } }
            ?.filter { it.second.isNotBlank() }
            ?.toMap()
            .orEmpty()
        val candidates = extractStreamCandidates(playurl)
        val picked = candidates.firstOrNull { it.first == "flv" } ?: candidates.firstOrNull()
        val streamUrl = picked?.second
        val accepted = picked?.third.orEmpty()
        val qualities = accepted.filter { streamUrl != null }.map { quality ->
            CorePlaybackQuality(quality, labels[quality]?.ifBlank { null } ?: fallbackQualityLabel(quality))
        }
        return CoreLiveStream(
            liveStatus = data.intValue("live_status"),
            isPortrait = data.booleanValue("is_portrait"),
            encrypted = data.booleanValue("encrypted"),
            pwdVerified = data.booleanValue("pwd_verified"),
            qualities = qualities,
            urlsByQuality = qualities.associate { it.id to streamUrl.orEmpty() }.filterValues { it.isNotBlank() },
        )
    }

    // Triple of (format name, play url, accepted qn list) for every https codec of the room.
    private fun extractStreamCandidates(playurl: JsonObject?): List<Triple<String, String, List<Int>>> {
        val protocols = playurl?.arrayValue("stream")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty()
        return protocols.filter { it.text("protocol_name").contains("https") }.flatMap { protocol ->
            protocol.arrayValue("format")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty().flatMap { format ->
                val formatName = format.text("format_name")
                val masterUrl = format.text("master_url")
                format.arrayValue("codec")?.mapNotNull { it.asJsonObjectOrNull() }.orEmpty().mapNotNull { codec ->
                    val accepted = buildList {
                        codec.intValue("current_qn").takeIf { it > 0 }?.let(::add)
                        codec.arrayValue("accept_qn")?.mapNotNull { it.asIntOrNullSafe() }?.forEach(::add)
                    }.distinct().sortedDescending()
                    val url = when (formatName) {
                        "flv" -> {
                            val baseUrl = codec.text("base_url")
                            val first = codec.arrayValue("url_info")?.mapNotNull { it.asJsonObjectOrNull() }?.firstOrNull()
                            val host = first?.text("host").orEmpty()
                            (host + baseUrl + first?.text("extra").orEmpty()).takeIf { host.isNotBlank() && baseUrl.isNotBlank() }
                        }
                        "hls" -> masterUrl.ifBlank {
                            codec.arrayValue("url_info")?.mapNotNull { it.asJsonObjectOrNull() }
                                ?.firstNotNullOfOrNull { entry -> entry.text("host").takeIf { it.isNotBlank() } }
                                ?.let { it + codec.text("base_url") }
                        }
                        else -> masterUrl.takeIf { it.isNotBlank() }
                    }
                    url?.takeIf { accepted.isNotEmpty() }?.let { Triple(formatName, it, accepted) }
                }
            }
        }
    }

    override suspend fun loadRecommend(page: Int): List<CoreLiveRoom> {
        val url = client.signedBilibiliUrl(
            "https://api.live.bilibili.com/xlive/web-interface/v1/second/getUserRecommend",
            mapOf("page" to page.toString(), "page_size" to "10", "platform" to "web"),
        )
        return parseRoomList(client.getBilibiliJson(url).requireBilibiliData())
    }

    override suspend fun loadFollowed(page: Int, cookie: String): List<CoreLiveRoom> {
        val url = "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList?page=$page&page_size=10"
        return parseRoomList(client.getBilibiliJson(url, cookie).requireBilibiliData())
    }

    private fun parseRoomList(data: JsonObject): List<CoreLiveRoom> {
        val list = data.arrayValue("list") ?: data.arrayValue("rooms") ?: return emptyList()
        return list.mapNotNull { entry -> entry.asJsonObjectOrNull()?.let(::parseRoom) }
    }

    private fun fixCoverUrl(url: String) = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }

    private fun parseRoom(data: JsonObject): CoreLiveRoom = CoreLiveRoom(
        roomId = data.longValue("room_id").takeIf { it > 0 } ?: data.longValue("roomid"),
        uid = data.longValue("uid"),
        title = data.text("title"),
        uname = data.text("uname"),
        cover = fixCoverUrl(data.text("user_cover").ifBlank { data.text("cover") }.ifBlank { data.text("keyframe") }),
        face = fixCoverUrl(data.text("face")),
        areaName = data.text("area_name").ifBlank { data.text("area_v2_name") },
        liveStatus = data.intValue("live_status"),
        online = data.intValue("online"),
        isPortrait = data.booleanValue("is_portrait"),
    )
}

internal fun fallbackQualityLabel(qn: Int): String = when (qn) {
    30000 -> "1080P+"
    20000 -> "4K"
    10000 -> "原画"
    400 -> "蓝光"
    250 -> "超清"
    150 -> "高清"
    else -> "标清"
}
