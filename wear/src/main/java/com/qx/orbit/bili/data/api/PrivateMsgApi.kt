package com.qx.orbit.bili.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qx.orbit.bili.data.model.PrivateMessage
import com.qx.orbit.bili.data.model.PrivateMessagePage
import com.qx.orbit.bili.data.model.PrivateMsgSession
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object PrivateMsgApi {
    private val api by lazy { BiliApiService.create() }

    suspend fun getPrivateMsgPage(
        talkerId: Long,
        size: Int,
        beginSeqno: Long,
        endSeqno: Long
    ): PrivateMessagePage = withContext(Dispatchers.IO) {
        val root = api.getPrivateMsg(talkerId, 1, size, beginSeqno, endSeqno).body()
        val data = root.objectValue("data") ?: return@withContext PrivateMessagePage()
        PrivateMessagePage(
            messages = parseMessages(data),
            hasMore = data.intValue("has_more") == 1
        )
    }

    suspend fun getPrivateMsg(
        talkerId: Long,
        size: Int,
        beginSeqno: Long,
        endSeqno: Long
    ): List<PrivateMessage> = getPrivateMsgPage(talkerId, size, beginSeqno, endSeqno).messages

    suspend fun getPrivateMsgList(allMsgJson: JsonElement): List<PrivateMessage> =
        withContext(Dispatchers.Default) {
            val root = allMsgJson.objectOrNull() ?: return@withContext emptyList()
            parseMessages(root.objectValue("data") ?: root)
        }

    suspend fun getUsersInfo(uidList: List<Long>): Map<Long, UserInfo> = withContext(Dispatchers.IO) {
        if (uidList.isEmpty()) return@withContext emptyMap()
        val uids = uidList.distinct().joinToString(",")
        val request = Request.Builder()
            .url("https://api.vc.bilibili.com/account/v1/user/cards?uids=$uids")
            .build()
        val responseBody = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.string()
        }
        val root = GsonConfig.gson.fromJson(responseBody, JsonObject::class.java)
        val data = root.arrayValue("data")
            ?: root.objectValue("data")?.arrayValue("cards")
            ?: JsonArray()
        data.mapNotNull { element ->
            val user = element.objectOrNull() ?: return@mapNotNull null
            val mid = user.longValue("mid")
            if (mid <= 0L) return@mapNotNull null
            mid to UserInfo(
                mid = mid,
                name = user.stringValue("name"),
                avatar = user.stringValue("face"),
                sign = user.stringValue("sign"),
                fans = user.intValue("fans"),
                following = user.intValue("attention")
            )
        }.toMap()
    }

    suspend fun getSessionsList(size: Int): List<PrivateMsgSession> = withContext(Dispatchers.IO) {
        val data = api.getSessions(size).body().objectValue("data")
            ?: return@withContext emptyList()
        val sessions = data.arrayValue("session_list") ?: return@withContext emptyList()
        sessions.mapNotNull { element ->
            val session = element.objectOrNull() ?: return@mapNotNull null
            if (session.has("account_info") && !session.get("account_info").isJsonNull) {
                return@mapNotNull null
            }
            val talkerId = session.longValue("talker_id")
            if (talkerId <= 0L) return@mapNotNull null
            val lastMessage = session.objectValue("last_msg")
            PrivateMsgSession(
                talkerUid = talkerId,
                unread = session.intValue("unread_count"),
                contentType = lastMessage?.intValue("msg_type") ?: 0,
                content = lastMessage?.get("content").parseNestedJson(),
                timestamp = lastMessage?.longValue("timestamp") ?: 0L,
                lastMsgSeqno = lastMessage?.longValue("msg_seqno") ?: 0L,
                sessionType = session.intValue("session_type").takeIf { it > 0 } ?: 1
            )
        }
    }

    suspend fun sendMsg(
        senderUid: Long,
        receiverUid: Long,
        msgType: Int,
        timestamp: Long,
        content: String
    ) = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val fields = mapOf(
            "msg[dev_id]" to UUID.randomUUID().toString().uppercase(Locale.ROOT),
            "msg[msg_type]" to msgType.toString(),
            "msg[content]" to content,
            "msg[receiver_type]" to "1",
            "msg[sender_uid]" to senderUid.toString(),
            "msg[receiver_id]" to receiverUid.toString(),
            "msg[timestamp]" to timestamp.toString(),
            "csrf" to csrf
        )
        api.sendPrivateMsg(fields).body()
        Unit
    }

    suspend fun updateAck(talkerId: Long, sessionType: Int, ackSeqno: Long) =
        withContext(Dispatchers.IO) {
            val csrf = CookieManager.getCsrf()
            val fields = buildMap {
                put("talker_id", talkerId.toString())
                put("session_type", sessionType.toString())
                if (ackSeqno > 0L) put("ack_seqno", ackSeqno.toString())
                put("csrf_token", csrf)
                put("csrf", csrf)
                put("build", "0")
                put("mobi_app", "web")
            }
            api.updateAck(fields).body()
            Unit
        }

    private fun parseMessages(data: JsonObject): List<PrivateMessage> {
        val messages = data.arrayValue("messages") ?: return emptyList()
        return messages.mapNotNull { element ->
            val message = element.objectOrNull() ?: return@mapNotNull null
            PrivateMessage(
                content = message.get("content").parseNestedJson(),
                type = message.intValue("msg_type"),
                timestamp = message.longValue("timestamp"),
                uid = message.longValue("sender_uid"),
                msgId = message.longValue("msg_key"),
                msgSeqno = message.longValue("msg_seqno"),
                msgSource = message.intValue("msg_source")
            )
        }
    }

    private fun Result<JsonElement>.body(): JsonObject = when (this) {
        is Result.Success -> data.objectOrNull() ?: error("响应格式错误")
        is Result.Error -> throw exception
    }

    private fun JsonElement?.parseNestedJson(): JsonElement? {
        if (this == null || isJsonNull) return null
        if (isJsonObject || isJsonArray) return this
        val raw = runCatching { asString }.getOrNull().orEmpty().trim()
        if (!raw.startsWith("{") && !raw.startsWith("[")) return this
        return runCatching { JsonParser.parseString(raw) }.getOrNull() ?: this
    }

    private fun JsonElement.objectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name)?.objectOrNull()

    private fun JsonObject.arrayValue(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringValue(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

    private fun JsonObject.longValue(name: String): Long =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L

    private fun JsonObject.intValue(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() } ?: 0
}
