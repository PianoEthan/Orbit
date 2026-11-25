package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliMessageGatewayTest {

    private fun gateway(responder: (okhttp3.Request) -> String): BilibiliMessageGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(responder(request).toResponseBody()).build()
        }.build()
        return BilibiliMessageGateway(client)
    }

    @Test fun mergesFeedAndPrivateUnread() = runBlocking {
        val unread = gateway { request ->
            when {
                request.url.encodedPath.endsWith("msgfeed/unread") ->
                    """{"code":0,"data":{"at":1,"like":2,"reply":3,"sys_msg":4}}"""
                else -> """{"code":0,"data":{"unfollow_unread":5,"follow_unread":1,"dustbin_unread":2}}"""
            }
        }.loadUnread("cookie")
        assertEquals(1, unread.at)
        assertEquals(2, unread.like)
        assertEquals(8, unread.privateMessage)
        assertEquals(18, unread.total)
    }

    @Test fun parsesLikeNoticesWithAggregatedUsers() = runBlocking {
        val page = gateway {
            """{"code":0,"data":{"items":[{"id":7,"counts":3,"like_time":1700000000,
               "users":[{"mid":11,"nickname":"甲","face":"https://f/11.jpg"},{"mid":12,"nickname":"乙","face":"https://f/12.jpg"}],
               "item":{"type":"video","item_id":0,"subject_id":99,"title":"被赞视频"}}],
               "cursor":{"is_end":false,"id":7,"time":1700000000}}}"""
        }.loadNotices(CoreNoticeKind.LIKE, 0, 0, "cookie")
        val notice = page.items.single()
        assertEquals(listOf("甲", "乙"), notice.users.map { it.name })
        assertEquals("等总共 3 人点赞了你的视频", notice.content)
        assertEquals(99L, notice.itemId)
        assertEquals(1700000000L, notice.timeStamp)
        assertFalse(page.isEnd)
        assertEquals(7L, page.nextId)
    }

    @Test fun parsesReplyNoticesAndCursorParam() = runBlocking {
        var url: okhttp3.HttpUrl? = null
        val page = gateway { request ->
            url = request.url
            """{"code":0,"data":{"items":[{"id":3,"reply_time":1600000000,
               "user":{"mid":21,"name":"丙","face":"https://f/21.jpg"},
               "item":{"type":"reply","target_id":55,"root_id":44,"source_content":"原文"}}],
               "cursor":{"is_end":true}}}"""
        }.loadNotices(CoreNoticeKind.REPLY, 100, 200, "cookie")
        assertEquals(55L, page.items.single().targetId)
        assertEquals("丙", page.items.single().users.single().name)
        assertEquals("原文", page.items.single().content)
        assertEquals("200", url!!.queryParameter("reply_time"))
        assertEquals("100", url!!.queryParameter("id"))
    }

    @Test fun unloggedResponsesYieldEmptyPage() = runBlocking {
        val page = gateway { """{"code":-101,"message":"账号未登录","data":null}""" }
            .loadNotices(CoreNoticeKind.AT, 0, 0, "")
        assertTrue(page.items.isEmpty())
        assertTrue(page.isEnd)
    }
}
