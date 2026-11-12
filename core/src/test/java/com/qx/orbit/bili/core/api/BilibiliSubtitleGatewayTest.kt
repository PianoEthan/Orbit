package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliSubtitleGatewayTest {
    @Test fun readsTracksWithSelectedCidAndDoesNotSendSessionToCdn() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("nav") -> """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
                request.url.host.endsWith("hdslb.com") -> {
                    assertNull(request.header("Cookie"))
                    """{"body":[{"from":1.1,"to":2.3,"content":"字幕"},{"from":-1,"to":0,"content":"invalid"}]}"""
                }
                else -> {
                    assertEquals("102", request.url.queryParameter("cid"))
                    assertEquals("SESSDATA=test", request.header("Cookie"))
                    """{"code":0,"data":{"subtitle":{"subtitles":[{"id":7,"lan":"zh-CN","lan_doc":"中文","subtitle_url":"//aisubtitle.hdslb.com/test.json","ai_status":1},{"id":8,"subtitle_url":"https://untrusted.test/track.json"}]}}}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliSubtitleGateway(client)
        val tracks = gateway.loadTracks(CoreVideo(1, "BV1", "", "", "", cid = 102), "SESSDATA=test")
        assertEquals(1, tracks.size)
        assertTrue(tracks.single().generated)
        assertEquals("中文", tracks.single().label)
        assertEquals(listOf(CoreSubtitleCue(1100, 2300, "字幕")), gateway.loadCues(tracks.single(), "SESSDATA=test"))
    }
}
