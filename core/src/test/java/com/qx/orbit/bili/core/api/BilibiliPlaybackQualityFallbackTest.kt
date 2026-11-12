package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliPlaybackQualityFallbackTest {
    private val video = CoreVideo(1, "BVtest", "视频", "", "UP", cid = 101)
    private val nav = """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
    private val mp4 = """{"code":0,"data":{"quality":64,"accept_quality":[64,16],"accept_description":["720P","360P"],"durl":[{"url":"https://cdn.test/720.mp4","length":120000}]}}"""
    private fun dash(quality: Int) = """{"code":0,"data":{"accept_quality":[80,64,32,16],"accept_description":["1080P","720P","480P","360P"],"dash":{"video":[{"id":$quality,"codecs":"avc1.640032","baseUrl":"https://cdn.test/$quality.m4s"}],"audio":[{"codecs":"mp4a.40.2","baseUrl":"https://cdn.test/audio.m4s"}]}}}"""

    @Test fun initialMp4MenuIncludesDash1080pAndSelectingItReturnsReal1080pWithAudio() = runBlocking {
        val cookies = mutableListOf<String?>()
        val requests = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                cookies += request.header("Cookie")
                val format = request.url.queryParameter("fnval")
                requests += format
                if (format == "1") mp4 else dash(if (request.url.queryParameter("qn") == "80") 80 else 32)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliPlaybackGateway(client, cookieProvider = { "SESSDATA=test; DedeUserID=123" })
        val initial = gateway.resolveStream(video, 32, true)
        assertNull(initial.audioUrl)
        assertTrue(initial.qualities.any { it.id == 80 && it.label == "1080P" })
        val selected = gateway.resolveStream(video, 80, true)
        assertEquals(80, selected.quality)
        assertEquals(listOf("https://cdn.test/80.m4s"), selected.urls)
        assertEquals("https://cdn.test/audio.m4s", selected.audioUrl)
        assertEquals(listOf("1", "144", "1", "144"), requests)
        assertTrue(cookies.all { it == "SESSDATA=test; DedeUserID=123" })
    }

    @Test fun denied1080pStaysHonestAboutActualQuality() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("nav") -> nav
                request.url.queryParameter("fnval") == "1" -> mp4
                else -> dash(32)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliPlaybackGateway(client).resolveStream(video, 80, true)
        assertEquals(64, result.quality)
        assertNull(result.audioUrl)
        assertTrue(result.qualities.any { it.id == 80 })
    }

    @Test fun failedDashProbeKeepsPlayableMp4() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("nav") -> nav
                request.url.queryParameter("fnval") == "1" -> mp4
                else -> """{"code":-403,"message":"风控"}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliPlaybackGateway(client).resolveStream(video, 80, true)
        assertEquals(64, result.quality)
        assertEquals(listOf("https://cdn.test/720.mp4"), result.urls)
    }
}
