package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliSearchGatewayTest {
    @Test fun encodesKeywordAndMapsHighlightedResults() = runBlocking {
        var searchRequest: okhttp3.Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                searchRequest = request
                """{"code":0,"data":{"result":[{"aid":42,"bvid":"BV42","title":"<em>测试</em> &amp; 视频","pic":"//cover.test/42.jpg","author":"UP","play":124000,"duration":"12:34","cid":456}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        val videos = BilibiliSearchGateway(client).searchVideos("测试 & 视频", 2)
        assertEquals("测试 & 视频", searchRequest!!.url.queryParameter("keyword"))
        assertEquals("video", searchRequest!!.url.queryParameter("search_type"))
        assertEquals("2", searchRequest!!.url.queryParameter("page"))
        assertEquals(32, searchRequest!!.url.queryParameter("w_rid")!!.length)
        assertEquals("测试 & 视频", videos.single().title)
        assertEquals("https://cover.test/42.jpg", videos.single().cover)
        assertEquals(42L, videos.single().id)
        assertEquals("12.4万", videos.single().viewText)
        assertEquals("12:34", videos.single().durationText)
        assertEquals(456L, videos.single().cid)
    }

    @Test fun supportsNumericAndTextViewCountsAndPadsDuration() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                """{"code":0,"data":{"result":[{"bvid":"BV1","play":"3.4万","duration":"26:4"},{"bvid":"BV2","play":"3500","duration":"0:9"},{"bvid":"BV3","play":0},{"bvid":"BV4","play":null}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        val videos = BilibiliSearchGateway(client).searchVideos("测试", 1)
        assertEquals(listOf("3.4万", "3500", "0", ""), videos.map { it.viewText })
        assertEquals(listOf("26:04", "0:09", "", ""), videos.map { it.durationText })
    }

    @Test fun parsesHotwordsAndSuggestions() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when {
                request.url.encodedPath.endsWith("hotword") ->
                    """{"code":0,"list":[{"keyword":"热词1","show_name":"热词1","icon":""},{"keyword":"热词2","show_name":"热词二"}]}"""
                request.url.encodedPath.endsWith("suggest") ->
                    """{"code":0,"result":{"tag":[{"value":"建议1"},{"value":"建议2"}]}}"""
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliSearchGateway(client)
        val hotwords = gateway.loadHotwords()
        assertEquals(2, hotwords.size)
        assertEquals("热词1", hotwords[0].keyword)
        assertEquals("热词二", hotwords[1].showName)

        val suggestions = gateway.loadSuggestions("测试")
        assertEquals(listOf("建议1", "建议2"), suggestions)
        assertEquals(emptyList<String>(), gateway.loadSuggestions("   "))
    }

    @Test fun blankSearchDoesNotPerformNetworkRequest() = runBlocking {
        try {
            BilibiliSearchGateway().searchVideos("  ", 1)
            fail("Expected validation failure")
        } catch (expected: IllegalArgumentException) {
            assertEquals("请输入搜索内容", expected.message)
        }
    }
}
