package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliDynamicGatewayTest {
    @Test fun mapsTextPicturesForwardAndVideoAndUsesAllFeed() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("all", request.url.queryParameter("type"))
            assertEquals("session", request.header("Cookie"))
            val json = """{"code":0,"data":{"has_more":1,"offset":"next&value","items":[
                {"id_str":"1","modules":{"module_author":{"name":"用户","face":"//face.test/a","pub_time":"刚刚"},"module_dynamic":{"desc":{"text":"正文"},"major":{"type":"MAJOR_TYPE_DRAW","draw":{"items":[{"src":"http://pic.test/1"}]}}}}},
                {"id_str":"2","type":"DYNAMIC_TYPE_FORWARD","modules":{"module_dynamic":{"desc":{"text":"转发文字"}}},"orig":{"id_str":"3","modules":{"module_author":{"name":"原作者"},"module_dynamic":{"major":{"type":"MAJOR_TYPE_OPUS","opus":{"title":"图文标题","summary":{"text":"图文摘要"},"pics":[{"url":"//pic.test/2"}]}}}}}},
                {"id_str":"4","modules":{"module_author":{"name":"UP"},"module_dynamic":{"major":{"type":"MAJOR_TYPE_ARCHIVE","archive":{"bvid":"BV42","aid":"42","title":"视频","cover":"//pic.test/video","stat":{"play":"1.2万"}}}}}},
                {"id_str":"5","modules":{"module_dynamic":{"major":{"type":"MAJOR_TYPE_PGC"}}}},
                {"id_str":"6","type":"DYNAMIC_TYPE_FORWARD","orig":null}
            ]}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val page = BilibiliDynamicGateway(client).load("session", "")
        assertEquals("next&value", page.nextOffset)
        assertEquals(5, page.items.size)
        assertEquals("正文", page.items[0].text)
        assertEquals(listOf("https://pic.test/1"), page.items[0].images)
        assertEquals("原作者", page.items[1].original?.author)
        assertEquals("图文摘要", page.items[1].original?.text)
        assertEquals("BV42", page.items[2].video?.bvid)
        assertEquals("1.2万", page.items[2].video?.viewText)
        assertNotNull(page.items[3].unsupportedType)
        assertEquals("原动态已删除或暂不可见", page.items[4].unsupportedType)
    }

    @Test fun encodesOffsetAndStopsOnRepeatedOffset() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("next&value", request.url.queryParameter("offset"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"has_more":true,"offset":"next&value","items":null}}""".toResponseBody()).build()
        }.build()
        val page = BilibiliDynamicGateway(client).load("session", "next&value")
        assertTrue(page.items.isEmpty())
        assertNull(page.nextOffset)
    }
}
