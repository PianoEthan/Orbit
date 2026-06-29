package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.*
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PrivateMsgApi {

    private val api by lazy { BiliApiService.create() }

    internal data class SessionListData(
        @SerializedName("session_list") val session_list: List<SessionItem>? = null
    )

    internal data class SessionItem(
        @SerializedName("talker_id") val talker_id: Long = 0,
        @SerializedName("unread_count") val unread_count: Int = 0,
        @SerializedName("last_msg") val last_msg: LastMsg? = null,
        @SerializedName("session_type") val session_type: Int = 0
    )

    internal data class LastMsg(
        @SerializedName("msg_type") val msg_type: Int = 0,
        @SerializedName("content") val content: String? = null,
        @SerializedName("timestamp") val timestamp: Long = 0,
        @SerializedName("sender_uid") val sender_uid: Long = 0,
        @SerializedName("msg_seqno") val msg_seqno: Long = 0
    )

    internal data class FetchMsgData(
        @SerializedName("messages") val messages: List<MsgItemData>? = null
    )

    internal data class MsgItemData(
        @SerializedName("sender_uid") val sender_uid: Long = 0,
        @SerializedName("msg_type") val msg_type: Int = 0,
        @SerializedName("timestamp") val timestamp: Long = 0,
        @SerializedName("msg_seqno") val msg_seqno: Long = 0,
        @SerializedName("content") val content: JsonElement? = null
    )

    internal data class UserCardsData(
        @SerializedName("cards") val cards: List<UserCardItem>? = null
    )

    internal data class UserCardItem(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("sign") val sign: String? = null,
        @SerializedName("fans") val fans: Int = 0,
        @SerializedName("attention") val attention: Int = 0
    )

    suspend fun getPrivateMsg(talkerId: Long, size: Int, beginSeqno: Long, endSeqno: Long): List<PrivateMessage> = withContext(Dispatchers.IO) {
        when (val resp = api.getPrivateMsg(talkerId, 1, size, beginSeqno, endSeqno)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<FetchMsgData>>() {}.type
                val apiResp: ApiResponse<FetchMsgData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                data?.messages?.map { msg ->
                    PrivateMessage(content = msg.content, type = msg.msg_type, timestamp = msg.timestamp, uid = msg.sender_uid, msgId = msg.msg_seqno, msgSeqno = msg.msg_seqno)
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun getPrivateMsgList(allMsgJson: JsonElement): List<PrivateMessage> = withContext(Dispatchers.IO) {
        val type = object : TypeToken<ApiResponse<FetchMsgData>>() {}.type
        val resp: ApiResponse<FetchMsgData>? = GsonConfig.gson.fromJson(allMsgJson, type)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyList()
        resp.data.messages?.map { msg ->
            PrivateMessage(content = msg.content, type = msg.msg_type, timestamp = msg.timestamp, uid = msg.sender_uid, msgId = msg.msg_seqno, msgSeqno = msg.msg_seqno)
        } ?: emptyList()
    }

    suspend fun getUsersInfo(uidList: List<Long>): Map<Long, UserInfo> = withContext(Dispatchers.IO) {
        if (uidList.isEmpty()) return@withContext emptyMap()
        val uids = uidList.joinToString(",")
        val url = "https://api.bilibili.com/account/v1/user/cards?uids=$uids"
        val json = com.qx.orbit.bili.data.remote.HttpClient.client.newCall(
            okhttp3.Request.Builder().url(url)
                .addHeader("Cookie", CookieManager.getCookie())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                .build()
        ).execute().body?.string() ?: return@withContext emptyMap()
        val respType = object : TypeToken<ApiResponse<UserCardsData>>() {}.type
        val resp: ApiResponse<UserCardsData>? = GsonConfig.gson.fromJson(json, respType)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext emptyMap()
        resp.data.cards?.associate { card ->
            card.mid to UserInfo(mid = card.mid, name = card.name ?: "", avatar = card.face ?: "", sign = card.sign ?: "", fans = card.fans, following = card.attention)
        } ?: emptyMap()
    }

    suspend fun getSessionsList(size: Int): List<PrivateMsgSession> = withContext(Dispatchers.IO) {
        when (val resp = api.getSessions(size)) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<SessionListData>>() {}.type
                val apiResp: ApiResponse<SessionListData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = apiResp?.data
                data?.session_list?.map { session ->
                    val contentElement = session.last_msg?.content?.let {
                        try { GsonConfig.gson.fromJson(it, JsonElement::class.java) } catch (_: Exception) { null }
                    }
                    PrivateMsgSession(talkerUid = session.talker_id, unread = session.unread_count, contentType = session.last_msg?.msg_type ?: 0, content = contentElement)
                } ?: emptyList()
            }
            is Result.Error -> emptyList()
        }
    }

    suspend fun sendMsg(senderUid: Long, receiverUid: Long, msgType: Int, timestamp: Long, content: String): Int = withContext(Dispatchers.IO) {
        when (val resp = api.sendPrivateMsg(msgType, content, senderUid, receiverUid, timestamp, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }

    suspend fun updateAck(talkerId: Long, sessionType: Int, ackSeqno: Long): Int = withContext(Dispatchers.IO) {
        when (val resp = api.updateAck(talkerId, sessionType, ackSeqno, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> resp.exception.code
        }
    }
}
