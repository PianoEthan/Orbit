package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName

data class VideoInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val title: String = "",
    val staff: List<UserInfo> = emptyList(),
    @SerializedName("pic") val cover: String = "",
    @SerializedName("desc") val description: String = "",
    val duration: String = "",
    val stats: Stats? = null,
    val timeDesc: String = "",
    val pagenames: List<String> = emptyList(),
    val cids: List<Long> = emptyList(),
    val descAts: List<At> = emptyList(),
    val upowerExclusive: Boolean = false,
    val argueMsg: String? = null,
    val isCooperation: Boolean = false,
    val isSteinGate: Boolean = false,
    val is360: Boolean = false,
    val epid: Long = -1,
    val copyright: Int = 0,
    val collection: Collection? = null
) {
    companion object {
        const val COPYRIGHT_SELF = 1
        const val COPYRIGHT_REPRINT = 2
    }
}

data class VideoCard(
    val title: String = "",
    val upName: String = "",
    val view: String = "",
    @SerializedName("pic") val cover: String = "",
    val type: String = "video",
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val mid: Long = 0,
    val seasonId: Long = 0
)

data class Stats(
    val view: Int = 0,
    val like: Int = 0,
    val reply: Int = 0,
    val coin: Int = 0,
    val share: Int = 0,
    val danmaku: Int = 0,
    val favorite: Int = 0,
    val followed: Boolean = false,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val favoured: Boolean = false,
    val coined: Int = 0,
    val like_disabled: Boolean = false,
    val coin_disabled: Boolean = false,
    val fav_disabled: Boolean = false,
    val reply_disabled: Boolean = false,
    val share_disabled: Boolean = false,
    val coin_limit: Int = 0
)

data class At(
    val id: Long = 0,
    val start: Int = 0,
    val end: Int = 0,
    val name: String = ""
)

object StringUtil {
    fun toWan(num: Long): String = when {
        num >= 100_000_000 -> String.format("%.1f亿", num / 100_000_000.0)
        num >= 10_000 -> String.format("%.1f万", num / 10_000.0)
        else -> num.toString()
    }

    fun toTime(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    fun parseTime(time: String): Int {
        val parts = time.split(":")
        return when (parts.size) {
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            else -> 0
        }
    }
}
