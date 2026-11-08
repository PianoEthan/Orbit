package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BilibiliFeedGatewayTest {

    @Before
    fun setUp() {
        BilibiliDeviceFingerprint.clear()
    }

    @After
    fun tearDown() {
        BilibiliDeviceFingerprint.clear()
    }

    @Test
    fun recommendationReadsCurrentSessionForEveryRequest() = runBlocking {
        val cookies = mutableListOf<String?>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                cookies += request.header("Cookie")
                """{"code":0,"data":{"item":[]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        var session = ""
        val gateway = BilibiliFeedGateway(client, cookieProvider = { session })
        gateway.loadFeed(CoreFeed.RECOMMEND, 1)
        session = "SESSDATA=test-session; DedeUserID=123"
        gateway.loadFeed(CoreFeed.RECOMMEND, 1)
        session = ""
        gateway.loadFeed(CoreFeed.RECOMMEND, 1)
        assertEquals(listOf(null, "SESSDATA=test-session; DedeUserID=123", null), cookies)
    }

    @Test
    fun recommendationFiltersOutNonVideoItemsLikeLiveAndEmptyBvid() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                """
                {
                  "code": 0,
                  "data": {
                    "item": [
                      {
                        "id": 1001,
                        "bvid": "BV1valid",
                        "goto": "av",
                        "title": "有效视频",
                        "pic": "https://img.test/1.jpg",
                        "owner": {"name": "UP主1"},
                        "duration": 120,
                        "stat": {"view": 1000}
                      },
                      {
                        "id": 2001,
                        "bvid": "",
                        "goto": "live",
                        "title": "正在直播",
                        "pic": "https://img.test/live.jpg",
                        "owner": {"name": "主播"},
                        "duration": 0
                      },
                      {
                        "id": 3001,
                        "bvid": "",
                        "goto": "ad",
                        "title": "商业广告",
                        "pic": "https://img.test/ad.jpg"
                      },
                      {
                        "id": 4001,
                        "bvid": "   ",
                        "goto": "av",
                        "title": "空白BV",
                        "pic": "https://img.test/4.jpg"
                      }
                    ]
                  }
                }
                """.trimIndent()
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()

        val gateway = BilibiliFeedGateway(client)
        val videos = gateway.loadFeed(CoreFeed.RECOMMEND, 1)

        assertEquals(1, videos.size)
        assertEquals("BV1valid", videos[0].bvid)
        assertEquals(1001L, videos[0].id)
        assertEquals("有效视频", videos[0].title)
    }

    @Test
    fun recommendationIncludesFullParametersAndFingerprint() = runBlocking {
        var query = ""
        var requestCookie = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.encodedPath.endsWith("nav")) {
                val json = """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(json.toResponseBody()).build()
            } else {
                query = url.query.orEmpty()
                requestCookie = request.header("Cookie").orEmpty()
                val json = """{"code":0,"data":{"item":[]}}"""
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(json.toResponseBody()).build()
            }
        }.build()

        BilibiliDeviceFingerprint.cache(mapOf("buvid3" to "device_b3", "buvid4" to "device_b4"))
        val gateway = BilibiliFeedGateway(client, cookieProvider = { "SESSDATA=user_cookie; DedeUserID=100" })
        gateway.loadFeed(CoreFeed.RECOMMEND, 3)

        assertTrue(query.contains("fresh_idx=3"))
        assertTrue(query.contains("fresh_idx_1h=3"))
        assertTrue(query.contains("brush=3"))
        assertTrue(query.contains("web_location=1430650"))
        assertTrue(requestCookie.contains("SESSDATA=user_cookie"))
        assertTrue(requestCookie.contains("buvid3=device_b3"))
        assertTrue(requestCookie.contains("buvid4=device_b4"))
    }
}
