package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object MessageApi {

    private val api by lazy { BiliApiService.create() }

    data class UnreadData(
        @SerializedName("at") val at: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("reply") val reply: Int = 0,
        @SerializedName("sys_msg") val sys_msg: Int = 0,
        @SerializedName("up") val up: Int = 0
    )

    internal data class PrivateUnreadData(
        @SerializedName("unfollow_unread") val unfollow_unread: Int = 0,
        @SerializedName("follow_unread") val follow_unread: Int = 0
    )

    internal data class LikeMsgData(
        @SerializedName("total") val total: Int = 0,
        @SerializedName("items") val items: List<LikeMsgItem>? = null,
        @SerializedName("cursor") val cursor: MsgCursor? = null
    )

    internal data class MsgItems(
        @SerializedName("items") val items: List<LikeMsgItem>? = null,
        @SerializedName("cursor") val cursor: MsgCursor? = null
    )

    internal data class LikeMsgItem(
        @SerializedName("item") val item: LikeItemData? = null,
        @SerializedName("user") val user: MsgUser? = null,
        @SerializedName("reply_content") val reply_content: String? = null,
        @SerializedName("counts") val counts: Int = 0
    )

    internal data class MsgUser(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("nickname") val nickname: String? = null,
        @SerializedName("avatar") val avatar: String? = null
    )

    internal data class LikeItemData(
        @SerializedName("title") val title: String? = null,
        @SerializedName("business_id") val business_id: Int = 0,
        @SerializedName("item_id") val item_id: Long = 0,
        @SerializedName("subject_id") val subject_id: Long = 0,
        @SerializedName("type") val type: Int = 0,
        @SerializedName("uri") val uri: String? = null,
        @SerializedName("native_uri") val native_uri: String? = null
    )

    internal data class MsgCursor(
        @SerializedName("is_end") val is_end: Boolean = true,
        @SerializedName("id") val id: Long = 0,
        @SerializedName("time") val time: Long = 0
    )

    internal data class ReplyMsgData(
        @SerializedName("items") val items: List<ReplyMsgItem>? = null,
        @SerializedName("cursor") val cursor: MsgCursor? = null
    )

    internal data class ReplyMsgItem(
        @SerializedName("user") val user: MsgUser? = null,
        @SerializedName("item") val item: ReplyItemData? = null,
        @SerializedName("reply_content") val reply_content: String? = null,
        @SerializedName("counts") val counts: Int = 0
    )

    internal data class ReplyItemData(
        @SerializedName("subject_id") val subject_id: Long = 0,
        @SerializedName("root_id") val root_id: Long = 0,
        @SerializedName("target_reply_id") val target_reply_id: Long = 0,
        @SerializedName("business_id") val business_id: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("uri") val uri: String? = null,
        @SerializedName("native_uri") val native_uri: String? = null
    )

    internal data class AtMsgData(
        @SerializedName("items") val items: List<AtMsgItem>? = null,
        @SerializedName("cursor") val cursor: MsgCursor? = null
    )

    internal data class AtMsgItem(
        @SerializedName("user") val user: MsgUser? = null,
        @SerializedName("item") val at_item: AtItemData? = null,
        @SerializedName("reply_content") val reply_content: String? = null
    )

    internal data class AtItemData(
        @SerializedName("subject_id") val subject_id: Long = 0,
        @SerializedName("root_id") val root_id: Long = 0,
        @SerializedName("target_id") val target_id: Long = 0,
        @SerializedName("business_id") val business_id: Int = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("uri") val uri: String? = null,
        @SerializedName("native_uri") val native_uri: String? = null,
        @SerializedName("at_time") val at_time: Long = 0
    )

    internal data class SystemMsgData(
        @SerializedName("data") val data: SystemNotifyData? = null
    )

    internal data class SystemNotifyData(
        @SerializedName("items") val items: List<SystemNotifyItem>? = null
    )

    internal data class SystemNotifyItem(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("content") val content: String? = null,
        @SerializedName("time_at") val time_at: Long = 0,
        @SerializedName("type") val type: Int = 0
    )

    suspend fun getUnread(): UnreadData = withContext(Dispatchers.IO) {
        when (val resp = api.getUnread()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<UnreadData>>() {}.type
                val parsed: ApiResponse<UnreadData>? = GsonConfig.gson.fromJson(resp.data, type)
                parsed?.data ?: UnreadData()
            }
            is Result.Error -> UnreadData()
        }
    }

    suspend fun checkMessageUnread(): Int = withContext(Dispatchers.IO) {
        when (val resp = api.getUnread()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<UnreadData>>() {}.type
                val parsed: ApiResponse<UnreadData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = parsed?.data ?: return@withContext 0
                data.at + data.reply
            }
            is Result.Error -> 0
        }
    }

    suspend fun checkPrivateMsgUnread(): Int = withContext(Dispatchers.IO) {
        when (val resp = api.getPrivateMsgUnread()) {
            is Result.Success -> {
                val type = object : TypeToken<ApiResponse<PrivateUnreadData>>() {}.type
                val parsed: ApiResponse<PrivateUnreadData>? = GsonConfig.gson.fromJson(resp.data, type)
                val data = parsed?.data ?: return@withContext 0
                data.unfollow_unread + data.follow_unread
            }
            is Result.Error -> 0
        }
    }

    suspend fun getLikeMsg(id: Long, time: Long): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        when (val result = api.getLikeMsg(id, time)) {
            is Result.Error -> Pair(null, emptyList())
            is Result.Success -> {
                val resp: ApiResponse<LikeMsgData>? = GsonConfig.gson.fromJson(result.data, object : TypeToken<ApiResponse<LikeMsgData>>() {}.type)
                if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair(null, emptyList())
                val data = resp.data
                val cursor = if (data.cursor != null) MessageCard.Cursor(
                    is_end = data.cursor.is_end,
                    id = data.cursor.id,
                    time = data.cursor.time
                ) else null
                val list = data.items?.mapNotNull { item ->
                    val userInfo = item.user ?: return@mapNotNull null
                    MessageCard(
                        user = listOf(UserInfo(
                            mid = userInfo.mid,
                            name = userInfo.nickname ?: "",
                            avatar = userInfo.avatar ?: ""
                        )),
                        content = item.reply_content ?: "",
                        timeStamp = data.cursor?.time ?: 0,
                        subjectId = item.item?.subject_id ?: 0,
                        businessId = item.item?.business_id ?: 0,
                        getType = MessageCard.GET_TYPE_LIKE
                    )
                } ?: emptyList()
                Pair(cursor, list)
            }
        }
    }

    suspend fun getReplyMsg(id: Long, time: Long): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        when (val result = api.getReplyMsg(id, time)) {
            is Result.Error -> Pair(null, emptyList())
            is Result.Success -> {
                val resp: ApiResponse<ReplyMsgData>? = GsonConfig.gson.fromJson(result.data, object : TypeToken<ApiResponse<ReplyMsgData>>() {}.type)
                if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair(null, emptyList())
                val data = resp.data
                val cursor = if (data.cursor != null) MessageCard.Cursor(
                    is_end = data.cursor.is_end,
                    id = data.cursor.id,
                    time = data.cursor.time
                ) else null
                val list = data.items?.mapNotNull { item ->
                    val userInfo = item.user ?: return@mapNotNull null
                    MessageCard(
                        user = listOf(UserInfo(
                            mid = userInfo.mid,
                            name = userInfo.nickname ?: "",
                            avatar = userInfo.avatar ?: ""
                        )),
                        content = item.reply_content ?: "",
                        timeStamp = data.cursor?.time ?: 0,
                        subjectId = item.item?.subject_id ?: 0,
                        rootId = item.item?.root_id ?: 0,
                        targetId = item.item?.target_reply_id ?: 0,
                        businessId = item.item?.business_id ?: 0,
                        getType = MessageCard.GET_TYPE_REPLY
                    )
                } ?: emptyList()
                Pair(cursor, list)
            }
        }
    }

    suspend fun getAtMsg(id: Long, time: Long): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        when (val result = api.getAtMsg(id, time)) {
            is Result.Error -> Pair(null, emptyList())
            is Result.Success -> {
                val resp: ApiResponse<AtMsgData>? = GsonConfig.gson.fromJson(result.data, object : TypeToken<ApiResponse<AtMsgData>>() {}.type)
                if (resp == null || !resp.isSuccess || resp.data == null) return@withContext Pair(null, emptyList())
                val data = resp.data
                val cursor = if (data.cursor != null) MessageCard.Cursor(
                    is_end = data.cursor.is_end,
                    id = data.cursor.id,
                    time = data.cursor.time
                ) else null
                val list = data.items?.mapNotNull { item ->
                    val userInfo = item.user ?: return@mapNotNull null
                    MessageCard(
                        user = listOf(UserInfo(
                            mid = userInfo.mid,
                            name = userInfo.nickname ?: "",
                            avatar = userInfo.avatar ?: ""
                        )),
                        content = item.reply_content ?: "",
                        timeStamp = item.at_item?.at_time ?: 0,
                        subjectId = item.at_item?.subject_id ?: 0,
                        rootId = item.at_item?.root_id ?: 0,
                        targetId = item.at_item?.target_id ?: 0,
                        businessId = item.at_item?.business_id ?: 0,
                        getType = MessageCard.GET_TYPE_AT
                    )
                } ?: emptyList()
                Pair(cursor, list)
            }
        }
    }

    suspend fun getSystemMsg(): List<MessageCard> = withContext(Dispatchers.IO) {
        val url = "https://message.bilibili.com/x/sys-msg/query_user_notify"
        val json = httpGet(url)
        val type = object : TypeToken<SystemMsgData>() {}.type
        val resp: SystemMsgData? = GsonConfig.gson.fromJson(json, type)
        resp?.data?.items?.map { item ->
            MessageCard(
                id = item.id,
                content = item.content ?: "",
                timeStamp = item.time_at
            )
        } ?: emptyList()
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        return HttpClient.client.newCall(request).execute().body?.string() ?: ""
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
