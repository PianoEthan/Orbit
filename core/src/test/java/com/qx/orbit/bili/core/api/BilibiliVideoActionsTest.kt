package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliVideoActionsTest {
    @Test fun favoriteUsesAddMediaIdsAndNeverRemovesOtherFolders() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("test-cookie", request.header("Cookie"))
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals("42", fields["rid"])
            assertEquals("2", fields["type"])
            assertEquals("99", fields["add_media_ids"])
            assertEquals("", fields["del_media_ids"])
            assertEquals("csrf", fields["csrf"])
            assertFalse(fields.containsKey("media_id"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":0}""".toResponseBody()).build()
        }.build()
        assertTrue(BilibiliVideoGateway(client).favVideo(42, 99, "test-cookie", "csrf"))
    }

    @Test fun threadPageCarriesRootCookieAndPageAndMapsReplies() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/x/v2/reply/reply", request.url.encodedPath)
            assertEquals("42", request.url.queryParameter("oid"))
            assertEquals("99", request.url.queryParameter("root"))
            assertEquals("2", request.url.queryParameter("pn"))
            assertEquals("session", request.header("Cookie"))
            val json = """{"code":0,"data":{"root":{"rpid":99,"rcount":25,"member":{"uname":"原用户"},"content":{"message":"原评论"}},"replies":[{"rpid":101,"member":{"uname":"回复者"},"content":{"message":"回复内容"}}],"page":{"num":2,"size":20,"count":25}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val page = BilibiliVideoGateway(client).loadReplyThread(42, 99, 2, "session")
        assertEquals(25, page.root?.replyCount)
        assertEquals("回复内容", page.replies.single().message)
        assertFalse(page.hasMore)
    }

    @Test fun replyPageReadsCursorAndEncodesOffset() = runBlocking {
        val offsets = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                offsets += JsonParser.parseString(request.url.queryParameter("pagination_str")).asJsonObject.get("offset").asString
                """{"code":0,"data":{"replies":[{"rpid":1,"member":{"uname":"用户","avatar":"//avatar.test/a"},"content":{"message":"评论"},"like":3}],"cursor":{"all_count":2,"pagination_reply":{"next_offset":"next-offset"}}}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliVideoGateway(client)
        val first = gateway.loadReplyPage(42)
        assertEquals("next-offset", first.nextOffset)
        assertEquals("用户", first.replies.single().uname)
        val second = gateway.loadReplyPage(42, first.nextOffset!!)
        assertNull(second.nextOffset)
        assertEquals(listOf("", "next-offset"), offsets)
        assertEquals(2, second.totalCount)
    }
    @Test fun replyPageRespectsEndFlagEvenWithNonemptyOffset() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else """{"code":0,"data":{"replies":null,"cursor":{"is_end":true,"all_count":3,"pagination_reply":{"next_offset":"unused"}}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val page = BilibiliVideoGateway(client).loadReplyPage(42)
        assertTrue(page.replies.isEmpty())
        assertNull(page.nextOffset)
        assertEquals(3, page.totalCount)
    }

    @Test fun relationReadsAccountStateWithCookie() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("42", request.url.queryParameter("aid"))
            assertEquals("test-cookie", request.header("Cookie"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"like":true,"coin":2,"favorite":true}}""".toResponseBody()).build()
        }.build()
        assertEquals(CoreVideoRelation(true, 2, true), BilibiliVideoGateway(client).loadRelation(42, "test-cookie"))
    }

    @Test fun triplePostsAccountAndCsrfAndPreservesPartialResult() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("/x/web-interface/archive/like/triple", request.url.encodedPath)
            assertEquals("test-cookie", request.header("Cookie"))
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals("42", fields["aid"])
            assertEquals("test-csrf", fields["csrf"])
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"like":true,"coin":false,"fav":true,"multiply":0}}""".toResponseBody()).build()
        }.build()
        val result = BilibiliVideoGateway(client).tripleVideo(42, "test-cookie", "test-csrf")
        assertEquals(CoreTripleResult(true, false, true, 0), result)
        assertEquals(1, requests)
    }

    @Test fun tripleRejectsServerFailureAndMissingCredentials() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":-104,"message":"硬币不足"}""".toResponseBody()).build()
        }.build()
        val gateway = BilibiliVideoGateway(client)
        try { gateway.tripleVideo(42, "", ""); fail("Must reject unauthenticated call") } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
        try { gateway.tripleVideo(42, "cookie", "csrf"); fail("Must reject server error") } catch (error: IllegalStateException) { assertEquals("硬币不足", error.message) }
        assertEquals(1, calls)
    }
}
