package com.qx.orbit.bili.core.api

interface SessionGateway {
    suspend fun initializeSession()
    suspend fun refreshSession()
}

interface FeedGateway {
    suspend fun loadFeed(feed: CoreFeed, page: Int): List<CoreVideo>
    suspend fun removeVideo(video: CoreVideo)
}

enum class CoreFeed {
    RECOMMEND,
    POPULAR,
}

data class CoreVideo(
    val id: Long,
    val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val cid: Long = 0L,
    val durationText: String = "",
    val viewText: String = "",
    val startPositionMs: Long = 0L,
)
