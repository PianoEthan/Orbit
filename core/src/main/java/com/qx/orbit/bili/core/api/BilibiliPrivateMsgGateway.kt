package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import okhttp3.OkHttpClient

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

class BilibiliPrivateMsgGateway(private val client: OkHttpClient = OkHttpClient()) : PrivateMsgGateway {

    override suspend fun loadSessions(size: Int, cookie: String): List<CoreChatSession> {
        val data = client.getBilibiliJson(
            "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions?session_type=1&sort_type=1&size=$size",
            cookie,
        ).requireBilibiliData()
        return data.arrayValue("session_list")
            ?.mapNotNull { entry ->
                val session = entry.asJsonObjectOrNull() ?: return@mapNotNull null
                if (session.get("account_info")?.let { !it.isJsonNull } == true) return@mapNotNull null
                val talkerId = session.longValue("talker_id")
                if (talkerId <= 0L) return@mapNotNull null
                val last = session.objectValue("last_msg")
                CoreChatSession(
                    talkerId = talkerId,
                    unread = session.intValue("unread_count"),
                    sessionType = session.intValue("session_type").takeIf { it > 0 } ?: 1,
                    lastMessage = last?.let(::parseMessage),
                )
            }.orEmpty()
    }

    override suspend fun loadHistory(talkerId: Long, size: Int, beginSeqno: Long, endSeqno: Long, cookie: String): CoreChatHistoryPage {
        val data = client.getBilibiliJson(
            "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs?talker_id=$talkerId&session_type=1&size=$size&begin_seqno=$beginSeqno&end_seqno=$endSeqno",
            cookie,
        ).requireBilibiliData()
        val messages = data.arrayValue("messages")
            ?.mapNotNull { entry -> entry.asJsonObjectOrNull()?.let(::parseMessage) }
            .orEmpty()
        return CoreChatHistoryPage(messages, hasMore = data.intValue("has_more") == 1)
    }

    override suspend fun loadUsers(uids: List<Long>, cookie: String): List<CoreChatUser> {
        val distinct = uids.filter { it > 0 }.distinct()
        if (distinct.isEmpty()) return emptyList()
        val joined = distinct.joinToString(",")
        val root = client.getBilibiliJson(
            "https://api.vc.bilibili.com/account/v1/user/cards?uids=${URLEncoder.encode(joined, "UTF-8")}",
            cookie,
        )
        val data = root.arrayValue("data") ?: root.objectValue("data")?.arrayValue("cards") ?: return emptyList()
        return data.mapNotNull { entry ->
            val user = entry.asJsonObjectOrNull() ?: return@mapNotNull null
            val mid = user.longValue("mid")
            if (mid <= 0L) return@mapNotNull null
            CoreChatUser(mid = mid, name = user.text("name"), face = user.text("face"))
        }
    }

    override suspend fun sendText(senderUid: Long, receiverUid: Long, text: String, cookie: String, csrf: String): Boolean {
        val content = JsonObject().apply { addProperty("content", text) }
        val fields = mapOf(
            "msg[sender_uid]" to senderUid.toString(),
            "msg[receiver_id]" to receiverUid.toString(),
            "msg[receiver_type]" to "1",
            "msg[msg_type]" to "1",
            "msg[dev_id]" to UUID.randomUUID().toString().uppercase(Locale.ROOT),
            "msg[timestamp]" to (System.currentTimeMillis() / 1000).toString(),
            "msg[content]" to content.toString(),
            "csrf" to csrf,
        )
        val json = client.postBilibiliForm("https://api.vc.bilibili.com/web_im/v1/web_im/send_msg", cookie, fields)
        return json.get("code")?.asInt == 0
    }

    override suspend fun updateAck(talkerId: Long, sessionType: Int, ackSeqno: Long, cookie: String, csrf: String) {
        val fields = buildMap {
            put("talker_id", talkerId.toString())
            put("session_type", sessionType.toString())
            if (ackSeqno > 0L) put("ack_seqno", ackSeqno.toString())
            put("csrf", csrf)
            put("build", "0")
            put("mobi_app", "web")
        }
        client.postBilibiliForm("https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack", cookie, fields)
    }

    private fun parseMessage(data: JsonObject): CoreChatMessage {
        val type = data.intValue("msg_type")
        return CoreChatMessage(
            senderUid = data.longValue("sender_uid"),
            msgType = type,
            content = renderContent(type, data.get("content").nestedJson()),
            timestamp = data.longValue("timestamp"),
            seqno = data.longValue("msg_seqno"),
        )
    }

    private fun renderContent(msgType: Int, content: JsonElement?): String {
        if (content == null) return ""
        return when (msgType) {
            1 -> content.asJsonObjectOrNull()?.text("content").orEmpty()
            2 -> "[图片]"
            6, 8, 17, 182 -> "[媒体]"
            else -> runCatching { content.asString }.getOrNull() ?: "[非文本消息]"
        }
    }

    private fun JsonElement?.nestedJson(): JsonElement? {
        if (this == null || isJsonNull) return null
        if (isJsonObject || isJsonArray) return this
        val raw = runCatching { asString }.getOrNull().orEmpty().trim()
        if (!raw.startsWith("{") && !raw.startsWith("[")) return this
        return runCatching { JsonParser.parseString(raw) }.getOrNull() ?: this
    }
}
