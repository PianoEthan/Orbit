package com.qx.orbit.bili.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.qx.orbit.bili.data.model.MessageCard
import com.qx.orbit.bili.data.model.UserInfo
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.qx.orbit.bili.data.remote.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object MessageApi {
    private val api by lazy { BiliApiService.create() }

    data class UnreadData(
        @SerializedName("at") val at: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("reply") val reply: Int = 0,
        @SerializedName("sys_msg") val system: Int = 0,
        @SerializedName("up") val up: Int = 0
    )

    suspend fun getUnread(): UnreadData = withContext(Dispatchers.IO) {
        val data = api.getUnread().body().objectValue("data") ?: return@withContext UnreadData()
        UnreadData(
            at = data.intValue("at"),
            like = data.intValue("like"),
            reply = data.intValue("reply"),
            system = data.intValue("sys_msg"),
            up = data.intValue("up")
        )
    }

    suspend fun checkMessageUnread(): Int {
        val unread = getUnread()
        return unread.at + unread.reply
    }

    suspend fun checkPrivateMsgUnread(): Int = withContext(Dispatchers.IO) {
        val data = api.getPrivateMsgUnread().body().objectValue("data") ?: return@withContext 0
        PRIVATE_UNREAD_FIELDS.sumOf { field -> data.intValue(field) }
    }

    suspend fun getLikeMsg(
        id: Long,
        time: Long
    ): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        val data = api.getLikeMsg(id.positiveOrNull(), time.positiveOrNull())
            .body()
            .objectValue("data")
            ?: return@withContext null to emptyList()
        val items = data.objectValue("total")?.arrayValue("items")
            ?: data.arrayValue("items")
            ?: JsonArray()
        parseCursor(data) to items.mapNotNull { element ->
            val message = element.objectOrNull() ?: return@mapNotNull null
            val item = message.objectValue("item") ?: JsonObject()
            val users = message.arrayValue("users")
                ?.mapNotNull { it.objectOrNull()?.toUserInfo() }
                .orEmpty()
                .ifEmpty {
                    listOfNotNull(message.objectValue("user")?.toUserInfo())
                }
            val itemType = item.stringValue("type")
            val count = message.longValue("counts").coerceAtLeast(users.size.toLong())
            MessageCard(
                id = message.longValue("id"),
                user = users,
                timeStamp = message.longValue("like_time"),
                content = likeDescription(itemType, count),
                subjectId = item.longValue("item_id").takeIf { it > 0L }
                    ?: item.longValue("subject_id"),
                businessId = item.intValue("business_id"),
                itemType = itemType,
                getType = MessageCard.GET_TYPE_LIKE,
                sourceId = item.longValue("source_id"),
                rootId = item.longValue("root_id"),
                targetId = item.longValue("target_id"),
                targetTitle = item.stringValue("title"),
                targetImage = item.stringValue("image"),
                targetUri = item.stringValue("uri"),
                targetNativeUri = item.stringValue("native_uri")
            )
        }
    }

    suspend fun getReplyMsg(
        id: Long,
        time: Long
    ): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        val data = api.getReplyMsg(id.positiveOrNull(), time.positiveOrNull())
            .body()
            .objectValue("data")
            ?: return@withContext null to emptyList()
        val items = data.arrayValue("items") ?: JsonArray()
        parseCursor(data) to items.mapNotNull { element ->
            val message = element.objectOrNull() ?: return@mapNotNull null
            val item = message.objectValue("item") ?: JsonObject()
            MessageCard(
                id = message.longValue("id"),
                user = listOfNotNull(message.objectValue("user")?.toUserInfo()),
                timeStamp = message.longValue("reply_time"),
                content = item.stringValue("source_content")
                    .ifBlank { message.stringValue("reply_content") }
                    .ifBlank { "回复了你" },
                subjectId = item.longValue("subject_id"),
                businessId = item.intValue("business_id"),
                itemType = item.stringValue("type"),
                getType = MessageCard.GET_TYPE_REPLY,
                sourceId = item.longValue("source_id"),
                rootId = item.longValue("root_id"),
                targetId = item.longValue("target_id"),
                targetTitle = item.stringValue("title"),
                targetImage = item.stringValue("image"),
                targetUri = item.stringValue("uri"),
                targetNativeUri = item.stringValue("native_uri")
            )
        }
    }

    suspend fun getAtMsg(
        id: Long,
        time: Long
    ): Pair<MessageCard.Cursor?, List<MessageCard>> = withContext(Dispatchers.IO) {
        val data = api.getAtMsg(id.positiveOrNull(), time.positiveOrNull())
            .body()
            .objectValue("data")
            ?: return@withContext null to emptyList()
        val items = data.arrayValue("items") ?: JsonArray()
        parseCursor(data) to items.mapNotNull { element ->
            val message = element.objectOrNull() ?: return@mapNotNull null
            val item = message.objectValue("item") ?: JsonObject()
            MessageCard(
                id = message.longValue("id"),
                user = listOfNotNull(message.objectValue("user")?.toUserInfo()),
                timeStamp = message.longValue("at_time"),
                content = "提到了我",
                subjectId = item.longValue("subject_id"),
                businessId = item.intValue("business_id"),
                itemType = item.stringValue("type"),
                getType = MessageCard.GET_TYPE_AT,
                sourceId = item.longValue("source_id"),
                rootId = item.longValue("root_id"),
                targetId = item.longValue("target_id"),
                targetTitle = item.stringValue("title"),
                targetImage = item.stringValue("image"),
                targetUri = item.stringValue("uri"),
                targetNativeUri = item.stringValue("native_uri")
            )
        }
    }

    suspend fun getSystemMsg(): List<MessageCard> = withContext(Dispatchers.IO) {
        val csrf = CookieManager.getCsrf()
        val url = "https://message.bilibili.com/x/sys-msg/query_user_notify" +
            "?csrf=$csrf&page_size=35&build=0&mobi_app=web"
        val request = Request.Builder().url(url).build()
        val responseBody = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            response.body.string()
        }
        val root = GsonConfig.gson.fromJson(responseBody, JsonObject::class.java)
        val items = root.objectValue("data")?.arrayValue("system_notify_list") ?: JsonArray()
        items.mapNotNull { element ->
            val item = element.objectOrNull() ?: return@mapNotNull null
            val timestamp = item.longValue("time_at")
            MessageCard(
                id = item.longValue("id"),
                content = listOf(item.stringValue("title"), item.stringValue("content"))
                    .filter(String::isNotBlank)
                    .joinToString("\n"),
                timeStamp = timestamp,
                timeDesc = if (timestamp == 0L) item.stringValue("time_at") else ""
            )
        }
    }

    private fun parseCursor(data: JsonObject): MessageCard.Cursor? {
        val cursor = data.objectValue("cursor") ?: return null
        return MessageCard.Cursor(
            is_end = cursor.booleanValue("is_end", true),
            id = cursor.longValue("id"),
            time = cursor.longValue("time")
        )
    }

    private fun JsonObject.toUserInfo(): UserInfo = UserInfo(
        mid = longValue("mid"),
        name = stringValue("nickname").ifBlank { stringValue("name") },
        avatar = stringValue("avatar").ifBlank { stringValue("face") },
        fans = intValue("fans"),
        followed = booleanValue("follow")
    )

    private fun likeDescription(itemType: String, count: Long): String {
        val target = when (itemType) {
            "video" -> "视频"
            "reply" -> "评论"
            "dynamic", "album" -> "动态"
            "article" -> "专栏"
            else -> "内容"
        }
        return if (count > 1L) "等总共 $count 人点赞了你的$target" else "点赞了你的$target"
    }

    private fun Result<JsonElement>.body(): JsonObject = when (this) {
        is Result.Success -> data.objectOrNull() ?: error("响应格式错误")
        is Result.Error -> throw exception
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

    private fun JsonObject.booleanValue(name: String, default: Boolean = false): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } ?: default

    private fun Long.positiveOrNull(): Long? = takeIf { it > 0L }

    private val PRIVATE_UNREAD_FIELDS = listOf(
        "unfollow_unread",
        "follow_unread",
        "unfollow_push_msg",
        "dustbin_push_msg",
        "dustbin_unread",
        "biz_msg_unfollow_unread",
        "biz_msg_follow_unread",
        "custom_unread"
    )
}
