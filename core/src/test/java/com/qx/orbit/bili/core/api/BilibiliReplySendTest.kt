package com.qx.orbit.bili.core.api

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliReplySendTest {
    @Test fun postsRootAndParentForEveryReplyLevel() = runBlocking {
        val targets = listOf(0L to 0L, 99L to 99L, 99L to 101L)
        val received = mutableListOf<Pair<Long, Long>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("/x/v2/reply/add", request.url.encodedPath)
            assertEquals("fake-session", request.header("Cookie"))
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals("42", fields["oid"]); assertEquals("1", fields["type"])
            assertEquals("fake-csrf", fields["csrf"]); assertEquals("内容 & + 中文", fields["message"])
            received += fields.getValue("root").toLong() to fields.getValue("parent").toLong()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"rpid":123}}""".toResponseBody()).build()
        }.build()
        val gateway = BilibiliVideoGateway(client)
        targets.forEach { (root, parent) -> assertEquals(123L, gateway.sendReply(42, root, parent, "内容 & + 中文", "fake-session", "fake-csrf")) }
        assertEquals(targets, received)
    }

    @Test fun validatesBeforeNetworkAndDoesNotRetryTransportFailure() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { calls++; throw IOException("lost response") }.build()
        val gateway = BilibiliVideoGateway(client)
        val invalid = listOf(Triple(0L, 0L, 0L), Triple(42L, 0L, 99L), Triple(42L, 99L, 0L))
        invalid.forEach { (aid, root, parent) ->
            try { gateway.sendReply(aid, root, parent, "内容", "session", "csrf"); fail() } catch (_: IllegalArgumentException) { }
        }
        listOf("", "字".repeat(1001)).forEach {
            try { gateway.sendReply(42, 0, 0, it, "session", "csrf"); fail() } catch (_: IllegalArgumentException) { }
        }
        try { gateway.sendReply(42, 0, 0, "内容", "", "csrf"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
        try { gateway.sendReply(42, 0, 0, "内容", "session", "csrf"); fail() } catch (_: IOException) { }
        assertEquals(1, calls)
    }

    @Test fun distinguishesExplicitRejectionFromUnknownResponse() = runBlocking {
        val responses = ArrayDeque(listOf("""{"code":12002,"message":"评论区已关闭"}""", """{"code":0,"data":{}}""", """{"code":0,"data":{"need_captcha":true}}"""))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(responses.removeFirst().toResponseBody()).build()
        }.build()
        val gateway = BilibiliVideoGateway(client)
        try { gateway.sendReply(42, 0, 0, "内容", "session", "csrf"); fail() } catch (error: CoreReplyRejectedException) { assertEquals("评论区已关闭", error.message) }
        try { gateway.sendReply(42, 0, 0, "内容", "session", "csrf"); fail() } catch (error: IllegalStateException) { assertFalse(error is CoreReplyRejectedException) }
        try { gateway.sendReply(42, 0, 0, "内容", "session", "csrf"); fail() } catch (error: CoreReplyRejectedException) { assertTrue(error.message.orEmpty().contains("验证码")) }
    }
}
