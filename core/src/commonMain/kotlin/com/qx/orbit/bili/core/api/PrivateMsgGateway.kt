package com.qx.orbit.bili.core.api

data class CoreChatMessage(
    val senderUid: Long,
    val msgType: Int,
    val content: String,
    val timestamp: Long,
    val seqno: Long,
)

data class CoreChatSession(
    val talkerId: Long,
    val unread: Int,
    val sessionType: Int,
    val lastMessage: CoreChatMessage?,
)

data class CoreChatHistoryPage(
    val messages: List<CoreChatMessage>,
    val hasMore: Boolean,
)

data class CoreChatUser(
    val mid: Long,
    val name: String,
    val face: String,
)

interface PrivateMsgGateway {
    suspend fun loadSessions(size: Int, cookie: String): List<CoreChatSession>
    suspend fun loadHistory(talkerId: Long, size: Int, beginSeqno: Long, endSeqno: Long, cookie: String): CoreChatHistoryPage
    suspend fun loadUsers(uids: List<Long>, cookie: String): List<CoreChatUser>
    suspend fun sendText(senderUid: Long, receiverUid: Long, text: String, cookie: String, csrf: String): Boolean
    suspend fun updateAck(talkerId: Long, sessionType: Int, ackSeqno: Long, cookie: String, csrf: String)
}
