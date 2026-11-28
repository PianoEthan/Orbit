package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import java.net.URLEncoder
import okhttp3.OkHttpClient

class BilibiliMessageGateway(private val client: OkHttpClient = OkHttpClient()) : MessageGateway {

    override suspend fun loadUnread(cookie: String): CoreUnreadCount {
        val data = client.getBilibiliJson("https://api.bilibili.com/x/msgfeed/unread", cookie)
            .optBilibiliData()
        val privateData = client.getBilibiliJson(PRIVATE_UNREAD_URL, cookie).optBilibiliData()
        return CoreUnreadCount(
            at = data?.intValue("at") ?: 0,
            like = data?.intValue("like") ?: 0,
            reply = data?.intValue("reply") ?: 0,
            system = data?.intValue("sys_msg") ?: 0,
            privateMessage = PRIVATE_UNREAD_FIELDS.sumOf { field -> privateData?.intValue(field) ?: 0 },
        )
    }

    override suspend fun loadNotices(kind: CoreNoticeKind, id: Long, time: Long, cookie: String): CoreNoticePage {
        val params = buildList {
            add("platform=web"); add("build=0"); add("mobi_app=web")
            if (id > 0) add("id=$id")
            if (time > 0) add("${kind.queryParam}=$time")
        }.joinToString("&")
        val data = client.getBilibiliJson("${kind.endpoint}?$params", cookie).optBilibiliData()
            ?: return CoreNoticePage(emptyList())
        val items = data.objectValue("total")?.arrayValue("items") ?: data.arrayValue("items")
        val notices = items?.mapNotNull { entry ->
            val message = entry.asJsonObjectOrNull() ?: return@mapNotNull null
            parseNotice(kind, message)
        }.orEmpty()
        val cursor = data.objectValue("cursor")
        return CoreNoticePage(
            items = notices,
            isEnd = cursor?.booleanValue("is_end", true) ?: true,
            nextId = cursor?.longValue("id") ?: 0L,
            nextTime = cursor?.longValue("time") ?: 0L,
        )
    }

    override suspend fun loadSystemMessages(cookie: String, csrf: String): List<CoreNotice> {
        val url = "https://message.bilibili.com/x/sys-msg/query_user_notify?csrf=${URLEncoder.encode(csrf, "UTF-8")}&page_size=35&build=0&mobi_app=web"
        val data = client.getBilibiliJson(url, cookie).optBilibiliData() ?: return emptyList()
        return data.arrayValue("system_notify_list")
            ?.mapNotNull { entry ->
                val item = entry.asJsonObjectOrNull() ?: return@mapNotNull null
                CoreNotice(
                    id = item.longValue("id"),
                    users = emptyList(),
                    timeStamp = item.longValue("time_at"),
                    content = listOf(item.text("title"), item.text("content"))
                        .filter(String::isNotBlank)
                        .joinToString("\n"),
                )
            }.orEmpty()
    }

    private fun parseNotice(kind: CoreNoticeKind, message: JsonObject): CoreNotice? {
        val item = message.objectValue("item")
        val users = message.arrayValue("users")?.mapNotNull { it.asJsonObjectOrNull() }
            ?.map(::parseUser)
            .orEmpty()
            .ifEmpty { listOfNotNull(message.objectValue("user")?.let(::parseUser)) }
        if (kind == CoreNoticeKind.LIKE && users.isEmpty()) return null
        val itemType = item?.text("type").orEmpty()
        val count = message.longValue("counts").coerceAtLeast(users.size.toLong())
        val content = when (kind) {
            CoreNoticeKind.LIKE -> likeDescription(itemType, count)
            CoreNoticeKind.REPLY -> item?.text("source_content")
                .orEmpty().ifBlank { message.text("reply_content") }.ifBlank { "回复了你" }
            CoreNoticeKind.AT -> "提到了我"
        }
        return CoreNotice(
            id = message.longValue("id"),
            users = users,
            timeStamp = message.longValue(kind.stampField),
            content = content,
            itemType = itemType,
            itemId = (item?.longValue("item_id") ?: 0L).takeIf { it > 0L }
                ?: item?.longValue("subject_id") ?: 0L,
            rootId = item?.longValue("root_id") ?: 0L,
            targetId = item?.longValue("target_id") ?: 0L,
            targetTitle = item?.text("title").orEmpty(),
            targetUri = item?.text("uri").orEmpty(),
        )
    }

    private fun parseUser(data: JsonObject): CoreNoticeUser = CoreNoticeUser(
        mid = data.longValue("mid"),
        name = data.text("nickname").ifBlank { data.text("name") },
        face = data.text("avatar").ifBlank { data.text("face") },
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

    private companion object {
        const val PRIVATE_UNREAD_URL = "https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread"
        val PRIVATE_UNREAD_FIELDS = listOf(
            "unfollow_unread", "follow_unread", "unfollow_push_msg",
            "dustbin_push_msg", "dustbin_unread", "biz_msg_unfollow_unread",
            "biz_msg_follow_unread", "custom_unread",
        )
    }
}

private val CoreNoticeKind.endpoint: String
    get() = when (this) {
        CoreNoticeKind.REPLY -> "https://api.bilibili.com/x/msgfeed/reply"
        CoreNoticeKind.LIKE -> "https://api.bilibili.com/x/msgfeed/like"
        CoreNoticeKind.AT -> "https://api.bilibili.com/x/msgfeed/at"
    }

private val CoreNoticeKind.queryParam: String
    get() = if (this == CoreNoticeKind.AT) "at_time" else "reply_time"

private val CoreNoticeKind.stampField: String
    get() = when (this) {
        CoreNoticeKind.AT -> "at_time"
        CoreNoticeKind.LIKE -> "like_time"
        CoreNoticeKind.REPLY -> "reply_time"
    }

internal fun JsonObject.optBilibiliData(): JsonObject? =
    takeIf { get("code")?.asInt == 0 }?.objectValue("data")
