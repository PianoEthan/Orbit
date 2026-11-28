package com.qx.orbit.bili.core.api

interface PlaybackGateway {
    suspend fun resolve(video: CoreVideo): CorePlayback
    suspend fun resolveQuality(video: CoreVideo, quality: Int): CorePlayback = resolve(video)
    suspend fun resolveStream(video: CoreVideo, quality: Int, preferProgressive: Boolean = false): CorePlayback = resolveQuality(video, quality)
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
