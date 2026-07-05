package com.qx.orbit.bili.data.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class PlayerData(
    val title: String = "",
    val videoUrl: String = "",
    val danmakuUrl: String = "",
    val qn: Int = -1,
    val qnStrList: Array<String>? = null,
    val qnValueList: IntArray? = null,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val mid: Long = 0,
    val sid: Long = 0,
    val progress: Int = 0,
    val cidHistory: Long = 0,
    val type: Int = TYPE_VIDEO,
    val epid: Long = 0,
    val timeStamp: Long = 0,
    val pagenames: List<String> = emptyList(),
    val cids: List<Long> = emptyList(),
    val epids: List<Long> = emptyList(),
    val aids: List<Long> = emptyList(),
    val currentPageIndex: Int = 0,
    val dashData: DashData? = null,
    val audioUrl: String = ""
) {
    companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_BANGUMI = 1
        const val TYPE_LIVE = 2
        const val TYPE_LOCAL = 4
    }
}

data class DashData(
    val duration: Int = 0,
    val minBufferTime: Double = 0.0,
    val videoStreams: List<DashVideoStream> = emptyList(),
    val audioStreams: List<DashAudioStream> = emptyList(),
    val dolbyAudio: DashAudioStream? = null,
    val flacAudio: DashAudioStream? = null
) {
    fun getVideoStream(qn: Int): DashVideoStream? {
        val exact = videoStreams.find { it.id == qn }
        if (exact != null) return exact
        return videoStreams.filter { it.id <= qn }.maxByOrNull { it.id }
            ?: videoStreams.minByOrNull { kotlin.math.abs(it.id - qn) }
    }

    fun getBestAudioStream(): DashAudioStream? {
        if (flacAudio != null) return flacAudio
        if (dolbyAudio != null) return dolbyAudio
        return audioStreams.maxByOrNull { it.bandwidth }
    }

    companion object {
        fun fromJson(json: JSONObject): DashData {
            val video = mutableListOf<DashVideoStream>()
            json.optJSONArray("video")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { video.add(DashVideoStream.fromJson(it)) }
                }
            }
            val audio = mutableListOf<DashAudioStream>()
            json.optJSONArray("audio")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { audio.add(DashAudioStream.fromJson(it)) }
                }
            }
            return DashData(
                duration = json.optInt("duration"),
                minBufferTime = json.optDouble("minBufferTime"),
                videoStreams = video,
                audioStreams = audio
            )
        }
    }
}

data class DashVideoStream(
    val id: Int = 0,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("backupUrl") val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    val codecid: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject) = DashVideoStream(
            id = json.optInt("id"),
            baseUrl = json.optString("baseUrl") ?: json.optString("base_url"),
            bandwidth = json.optLong("bandwidth"),
            mimeType = json.optString("mimeType"),
            codecs = json.optString("codecs"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            frameRate = json.optString("frameRate"),
            codecid = json.optInt("codecid")
        )
    }
}

data class DashAudioStream(
    val id: Int = 0,
    @SerializedName("baseUrl") val baseUrl: String = "",
    @SerializedName("backupUrl") val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val mimeType: String = "",
    val codecs: String = "",
    val codecid: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject) = DashAudioStream(
            id = json.optInt("id"),
            baseUrl = json.optString("baseUrl") ?: json.optString("base_url"),
            bandwidth = json.optLong("bandwidth"),
            mimeType = json.optString("mimeType"),
            codecs = json.optString("codecs"),
            codecid = json.optInt("codecid")
        )
    }
}

data class Subtitle(
    val content: String = "",
    val from: Double = 0.0,
    val to: Double = 0.0
)

data class SubtitleLink(
    val id: Long = 0,
    val isAI: Boolean = false,
    val lang: String = "",
    val url: String = ""
)

data class ViewPoint(
    val content: String = "",
    val from: Int = 0,
    val to: Int = 0,
    val type: Int = 0,
    val imgUrl: String = "",
    val logoUrl: String = ""
)

data class HighEnergyData(
    val stepSec: Int = 0,
    val tagStr: String = "",
    val events: FloatArray = floatArrayOf(),
    val debug: String = ""
)
