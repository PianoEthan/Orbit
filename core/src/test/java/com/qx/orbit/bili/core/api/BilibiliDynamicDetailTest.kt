package com.qx.orbit.bili.core.api

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BilibiliDynamicDetailTest {
    @Test fun loadDetailParsesSingleItem() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/x/polymer/web-dynamic/v1/detail", request.url.encodedPath)
            assertEquals("99", request.url.queryParameter("id"))
            assertEquals("session", request.header("Cookie"))
            val json = """{"code":0,"data":{"item":{"id_str":"99","modules":{"module_author":{"name":"用户","face":"//face.test/a","pub_time":"刚刚"},"module_dynamic":{"desc":{"text":"详情正文"},"major":{"type":"MAJOR_TYPE_DRAW","draw":{"items":[{"src":"//pic.test/1"}]}}}}}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val dynamic = BilibiliDynamicGateway(client).loadDetail("99", "session")
        assertEquals("99", dynamic.id)
        assertEquals("详情正文", dynamic.text)
        assertEquals("用户", dynamic.author)
        assertEquals(listOf("https://pic.test/1"), dynamic.images)
    }

    @Test fun repostPostsCreateDynJsonWithCsrfAndRepostSource() = runBlocking {
        var body: String? = null
        var referer: String? = null
        var method: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            method = request.method
            referer = request.header("Referer")
            assertEquals("csrf-token", request.url.queryParameter("csrf"))
            assertEquals("session", request.header("Cookie"))
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            body = buffer.readUtf8()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":0}""".toResponseBody()).build()
        }.build()
        val ok = BilibiliDynamicGateway(client).repost("99", "转发说说", "session", "csrf-token")
        assertTrue(ok)
        assertEquals("POST", method)
        assertEquals("https://t.bilibili.com/", referer)
        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("99", json.getAsJsonObject("web_repost_src").get("dyn_id_str").asString)
        val dynReq = json.getAsJsonObject("dyn_req")
        assertEquals(4, dynReq.get("scene").asInt)
        val content = dynReq.getAsJsonObject("content").getAsJsonArray("contents").get(0).asJsonObject
        assertEquals("转发说说", content.get("raw_text").asString)
        assertEquals(1, content.get("type").asInt)
    }

    @Test fun repostBlankTextUsesDefaultAndReportsFailure() = runBlocking {
        var rawText: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            rawText = JsonParser.parseString(buffer.readUtf8()).asJsonObject
                .getAsJsonObject("dyn_req").getAsJsonObject("content").getAsJsonArray("contents").get(0).asJsonObject.get("raw_text").asString
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":-101,"message":"未登录"}""".toResponseBody()).build()
        }.build()
        val ok = BilibiliDynamicGateway(client).repost("99", "", "session", "csrf-token")
        assertFalse(ok)
        assertEquals("转发动态", rawText)
    }
}
