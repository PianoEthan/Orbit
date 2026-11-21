package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliFavEditTest {
    private fun client(capture: (okhttp3.Request, Map<String, String>) -> Unit, code: Int = 0) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as FormBody
            val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
            capture(request, fields)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":$code}""".toResponseBody()).build()
        }.build()

    @Test fun createFolderPostsAddForm() = runBlocking {
        var path = ""
        var fields = emptyMap<String, String>()
        val ok = BilibiliUserDataGateway(client({ req, f -> path = req.url.encodedPath; fields = f }))
            .createFavFolder("新夹", "简介", 1, "session", "csrf")
        assertTrue(ok)
        assertEquals("/x/v3/fav/folder/add", path)
        assertEquals("新夹", fields["title"])
        assertEquals("简介", fields["intro"])
        assertEquals("1", fields["privacy"])
        assertEquals("csrf", fields["csrf"])
    }

    @Test fun editFolderPostsEditFormWithMediaId() = runBlocking {
        var fields = emptyMap<String, String>()
        val ok = BilibiliUserDataGateway(client({ _, f -> fields = f }))
            .editFavFolder(7, "改名", "", 0, "session", "csrf")
        assertTrue(ok)
        assertEquals("7", fields["media_id"])
        assertEquals("改名", fields["title"])
        assertEquals("0", fields["privacy"])
    }

    @Test fun deleteFolderPostsDelForm() = runBlocking {
        var path = ""
        var fields = emptyMap<String, String>()
        val ok = BilibiliUserDataGateway(client({ req, f -> path = req.url.encodedPath; fields = f }))
            .deleteFavFolder(7, "session", "csrf")
        assertTrue(ok)
        assertEquals("/x/v3/fav/folder/del", path)
        assertEquals("7", fields["media_ids"])
    }

    @Test fun moveVideosPostsDealWithAddAndDelMediaIds() = runBlocking {
        var path = ""
        var fields = emptyMap<String, String>()
        val ok = BilibiliUserDataGateway(client({ req, f -> path = req.url.encodedPath; fields = f }))
            .moveFavVideos(listOf(11, 22), 1, 2, "session", "csrf")
        assertTrue(ok)
        assertEquals("/medialist/gateway/coll/resource/deal", path)
        assertEquals("11,22", fields["rid"])
        assertEquals("2", fields["type"])
        assertEquals("2", fields["add_media_ids"])
        assertEquals("1", fields["del_media_ids"])
    }

    @Test fun moveRejectsSameFolderAndReportsApiFailure() = runBlocking {
        val gateway = BilibiliUserDataGateway(client({ _, _ -> }, code = -101))
        assertFalse(gateway.moveFavVideos(listOf(11), 1, 2, "session", "csrf"))
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { gateway.moveFavVideos(listOf(11), 1, 1, "session", "csrf") }
        }
        Unit
    }
}
