package com.qx.orbit.bili.core.api

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliReplyPageTest {
    private val nav = """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
    private fun reply(id: Int) = """{"rpid":$id,"member":{"uname":"用户$id"},"content":{"message":"评论$id"}}"""

    @Test fun authenticatedPageCombinesPinnedSourcesBeforeRegularCommentsWithoutDuplicates() = runBlocking {
        val cookies = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                cookies += request.header("Cookie")
                val offset = JsonParser.parseString(request.url.queryParameter("pagination_str")).asJsonObject.get("offset").asString
                if (offset.isEmpty()) """{"code":0,"data":{"top_replies":[${reply(1)},${reply(2)}],"top":{"admin":${reply(1)},"upper":${reply(3)},"vote":null},"replies":[${reply(2)},${reply(4)}],"cursor":{"pagination_reply":{"next_offset":"next"}}}}"""
                else """{"code":0,"data":{"top_replies":[${reply(1)}],"replies":[${reply(5)}],"cursor":{"is_end":true}}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliVideoGateway(client)
        val first = gateway.loadReplyPage(42, "", "fake-session")
        assertEquals(listOf(1L, 2L, 3L, 4L), first.replies.map { it.rpid })
        assertEquals(listOf(true, true, true, false), first.replies.map { it.pinned })
        val next = gateway.loadReplyPage(42, "next", "new-session")
        assertEquals(listOf(5L), next.replies.map { it.rpid })
        assertFalse(next.replies.single().pinned)
        assertEquals(listOf("fake-session", "new-session"), cookies)
    }

    @Test fun singleTopObjectAndNullRegularListRemainReadable() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else """{"code":0,"data":{"top_replies":${reply(1)},"top":null,"replies":null,"cursor":{"is_end":true}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val page = BilibiliVideoGateway(client).loadReplyPage(42)
        assertEquals(1L, page.replies.single().rpid)
        assertTrue(page.replies.single().pinned)
        assertNull(page.nextOffset)
    }
}
