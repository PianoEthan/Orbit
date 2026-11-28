package com.qx.orbit.bili.core.api

data class CoreVideoPage(
    val page: Int,
    val part: String,
    val cid: Long,
    val durationSeconds: Int,
)

data class CoreVideoReply(
    val rpid: Long,
    val uname: String,
    val avatar: String,
    val message: String,
    val likeCount: Long,
    val replyCount: Int = 0,
    val pinned: Boolean = false,
)

data class CoreReplyThreadPage(val root: CoreVideoReply?, val replies: List<CoreVideoReply>, val hasMore: Boolean)

data class CoreReplyPage(val replies: List<CoreVideoReply>, val nextOffset: String?, val totalCount: Int)

data class CoreVideoDetails(
    val video: CoreVideo,
    val description: String,
    val durationSeconds: Int,
    val viewCount: Long,
    val likeCount: Long,
    val replyCount: Long,
    val pages: List<CoreVideoPage> = emptyList(),
    val ownerMid: Long = 0L,
    val ownerAvatar: String = "",
    val collectionTitle: String = "",
    val collectionVideos: List<CoreVideo> = emptyList(),
)

data class CoreTripleResult(val liked: Boolean, val coined: Boolean, val favorited: Boolean, val coinCount: Int)
data class CoreVideoRelation(val liked: Boolean, val coinCount: Int, val favorited: Boolean)

class CoreReplyRejectedException(message: String) : IllegalStateException(message)

interface VideoGateway {
    suspend fun sendReply(aid: Long, root: Long, parent: Long, message: String, cookie: String, csrf: String): Long = error("暂不支持发送评论")
    suspend fun loadDetails(video: CoreVideo): CoreVideoDetails
    suspend fun loadReplyPage(aid: Long, offset: String = ""): CoreReplyPage = CoreReplyPage(loadReplies(aid), null, 0)
    suspend fun loadReplyPage(aid: Long, offset: String, cookie: String): CoreReplyPage = loadReplyPage(aid, offset)
    suspend fun loadReplies(aid: Long): List<CoreVideoReply> = emptyList()
    suspend fun loadReplyThread(aid: Long, root: Long, page: Int, cookie: String): CoreReplyThreadPage = error("暂不支持评论回复")
    suspend fun loadRelation(aid: Long, cookie: String): CoreVideoRelation = error("暂不支持读取操作状态")
    suspend fun likeVideo(aid: Long, like: Boolean, cookie: String, csrf: String): Boolean = false
    suspend fun coinVideo(aid: Long, count: Int, cookie: String, csrf: String): Boolean = false
    suspend fun favVideo(aid: Long, folderId: Long, cookie: String, csrf: String): Boolean = false
    suspend fun tripleVideo(aid: Long, cookie: String, csrf: String): CoreTripleResult = error("暂不支持三连")
}
