package com.qx.orbit.bili.core.api

data class CoreUnreadCount(
    val at: Int = 0,
    val like: Int = 0,
    val reply: Int = 0,
    val system: Int = 0,
    val privateMessage: Int = 0,
) {
    val total: Int get() = at + like + reply + system + privateMessage
}

data class CoreNoticeUser(
    val mid: Long,
    val name: String,
    val face: String,
)

data class CoreNotice(
    val id: Long,
    val users: List<CoreNoticeUser>,
    val timeStamp: Long,
    val content: String,
    val itemType: String = "",
    val itemId: Long = 0L,
    val rootId: Long = 0L,
    val targetId: Long = 0L,
    val targetTitle: String = "",
    val targetUri: String = "",
)

data class CoreNoticePage(
    val items: List<CoreNotice>,
    val isEnd: Boolean = true,
    val nextId: Long = 0L,
    val nextTime: Long = 0L,
)

enum class CoreNoticeKind { REPLY, LIKE, AT }

interface MessageGateway {
    suspend fun loadUnread(cookie: String = ""): CoreUnreadCount
    suspend fun loadNotices(kind: CoreNoticeKind, id: Long, time: Long, cookie: String): CoreNoticePage
    suspend fun loadSystemMessages(cookie: String, csrf: String): List<CoreNotice>
}
