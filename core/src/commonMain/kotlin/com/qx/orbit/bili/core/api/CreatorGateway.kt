package com.qx.orbit.bili.core.api

data class CoreCreator(val mid: Long, val name: String, val avatar: String, val signature: String, val fans: Long, val following: Long, val followed: Boolean)
data class CoreCreatorVideos(val videos: List<CoreVideo>, val hasMore: Boolean)

interface CreatorGateway {
    suspend fun loadCreator(mid: Long, cookie: String): CoreCreator
    suspend fun loadVideos(mid: Long, page: Int, cookie: String): CoreCreatorVideos
    suspend fun changeRelation(mid: Long, action: Int, cookie: String, csrf: String)
}
