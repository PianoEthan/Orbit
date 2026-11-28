package com.qx.orbit.bili.core.api

data class CoreLiveRoom(
    val roomId: Long,
    val uid: Long = 0L,
    val title: String = "",
    val uname: String = "",
    val cover: String = "",
    val face: String = "",
    val areaName: String = "",
    val liveStatus: Int = 0,
    val online: Int = 0,
    val isPortrait: Boolean = false,
)

data class CoreLiveStream(
    val liveStatus: Int = 0,
    val isPortrait: Boolean = false,
    val encrypted: Boolean = false,
    val pwdVerified: Boolean = false,
    val qualities: List<CorePlaybackQuality> = emptyList(),
    val urlsByQuality: Map<Int, String> = emptyMap(),
) {
    fun requestQuality(preferred: Int): Int =
        qualities.map { it.id }.filter { it <= preferred }.maxOrNull()
            ?: qualities.minOfOrNull { it.id }
            ?: preferred
}

interface LiveGateway {
    suspend fun loadRoomInfo(roomId: Long): CoreLiveRoom
    suspend fun loadStreamInfo(roomId: Long, qn: Int, cookie: String = ""): CoreLiveStream
    suspend fun loadRecommend(page: Int): List<CoreLiveRoom>
    suspend fun loadFollowed(page: Int, cookie: String): List<CoreLiveRoom>
}
