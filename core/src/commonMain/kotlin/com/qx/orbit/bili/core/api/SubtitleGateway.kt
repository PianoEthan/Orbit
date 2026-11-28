package com.qx.orbit.bili.core.api

data class CoreSubtitleTrack(val id: Long, val language: String, val label: String, val url: String, val generated: Boolean)
data class CoreSubtitleCue(val fromMs: Long, val toMs: Long, val content: String)

interface SubtitleGateway {
    suspend fun loadTracks(video: CoreVideo, cookie: String): List<CoreSubtitleTrack>
    suspend fun loadCues(track: CoreSubtitleTrack, cookie: String): List<CoreSubtitleCue>
}
