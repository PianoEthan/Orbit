package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.FormBody
import org.junit.Assert.fail
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliUserGatewayTest {
    @Test fun dynamicVideoPageEncodesOffsetAndStopsRepeatedCursor() = runBlocking {
        val offset = "next+cursor&value"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals(offset, request.url.queryParameter("offset"))
            assertEquals("test-cookie", request.header("Cookie"))
            val json = """{"code":0,"data":{"has_more":true,"offset":"next+cursor&value","items":[{"modules":{"module_author":{"name":"UP"},"module_dynamic":{"major":{"archive":{"aid":"42","bvid":"BV42","title":"视频","cover":"//pic.test/42.jpg","duration_text":"1:23"}}}}}]}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliUserDataGateway(client).loadDynamicVideoPage("test-cookie", offset)
        assertEquals(null, result.nextOffset)
        assertEquals("BV42", result.videos.single().bvid)
        assertEquals("https://pic.test/42.jpg", result.videos.single().cover)
    }

    @Test fun historyCursorUsesServerPaginationAndCompletionProgress() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("history-cookie", request.header("Cookie"))
            val next = request.url.queryParameter("max") == "55"
            if (next) {
                assertEquals("1234", request.url.queryParameter("view_at"))
                assertEquals("archive", request.url.queryParameter("business"))
            }
            val json = if (next) """{"code":0,"data":{"list":null,"cursor":{"max":0,"view_at":0}}}""" else
                """{"code":0,"data":{"list":[{"title":"视频","history":{"oid":42,"bvid":"BV42","cid":99},"progress":-1,"duration":100}],"cursor":{"max":55,"view_at":1234,"business":"archive"}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliUserDataGateway(client)
        val first = gateway.loadHistoryPage("history-cookie")
        assertEquals(CoreHistoryCursor(55, 1234, "archive"), first.next)
        assertEquals(1f, first.items.single().progressPercent, 0f)
        assertEquals(42L, first.items.single().aid)
        val last = gateway.loadHistoryPage("history-cookie", first.next!!)
        assertEquals(null, last.next)
        assertEquals(emptyList<CoreHistoryItem>(), last.items)
    }

    @Test fun watchLaterAddAndRemoveUseCorrectFormsAndRequireCredentials() = runBlocking {
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            assertEquals("test-cookie", request.header("Cookie"))
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals("42", fields["aid"])
            assertEquals("csrf", fields["csrf"])
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":0}""".toResponseBody()).build()
        }.build()
        val gateway = BilibiliUserDataGateway(client)
        gateway.changeWatchLater(42, true, "test-cookie", "csrf")
        gateway.changeWatchLater(42, false, "test-cookie", "csrf")
        assertEquals(listOf("/x/v2/history/toview/add", "/x/v2/history/toview/del"), paths)
        try { gateway.changeWatchLater(42, true, "", ""); fail("Must reject missing account") } catch (_: IllegalArgumentException) { }
        assertEquals(2, paths.size)
    }

    @Test fun favoritePageUsesServerContinuationAndHandlesEmptyFolder() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("test-cookie", request.header("Cookie"))
            assertEquals("42", request.url.queryParameter("media_id"))
            val json = if (request.url.queryParameter("pn") == "1") {
                """{"code":0,"data":{"has_more":true,"medias":[{"id":1,"bv_id":"BV1","title":"视频","upper":{"name":"UP"}}]}}"""
            } else """{"code":0,"data":{"has_more":false,"medias":null}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliUserDataGateway(client)
        val first = gateway.loadFavVideoPage("test-cookie", 42, 1)
        assertEquals(true, first.hasMore)
        assertEquals("BV1", first.videos.single().bvid)
        val last = gateway.loadFavVideoPage("test-cookie", 42, 2)
        assertEquals(false, last.hasMore)
        assertEquals(emptyList<CoreVideo>(), last.videos)
    }

    @Test fun mapsHistoryAndWatchLater() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.encodedPath
            val json = when {
                url.endsWith("cursor") ->
                    """{"code":0,"data":{"list":[{"aid":101,"bvid":"BV101","title":"历史视频","cover":"//pic.test/1.jpg","author_name":"UP1","duration":100,"progress":50}]}}"""
                url.endsWith("toview") ->
                    """{"code":0,"data":{"list":[{"aid":202,"bvid":"BV202","title":"稍后再看","pic":"http://pic.test/2.jpg","owner":{"name":"UP2"}}]}}"""
                url.endsWith("list-all") ->
                    """{"code":0,"data":{"list":[{"id":303,"title":"默认收藏夹","media_count":12,"cover":"//pic.test/3.jpg"}]}}"""
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()

        val gateway = BilibiliUserDataGateway(client)
        val history = gateway.loadHistory("cookie")
        assertEquals(1, history.size)
        assertEquals("BV101", history[0].bvid)
        assertEquals(0.5f, history[0].progressPercent, 0.01f)

        val watchLater = gateway.loadWatchLater("cookie")
        assertEquals(1, watchLater.size)
        assertEquals("BV202", watchLater[0].bvid)
        assertEquals("UP2", watchLater[0].author)

        val folders = gateway.loadFavFolders("cookie", 123L)
        assertEquals(1, folders.size)
        assertEquals(303L, folders[0].id)
        assertEquals(12, folders[0].mediaCount)
    }
}
