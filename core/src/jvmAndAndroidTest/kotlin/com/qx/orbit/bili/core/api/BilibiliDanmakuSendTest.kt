package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliDanmakuSendTest {
    private fun client(code: Int, capture: (Map<String, String>) -> Unit) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/x/v2/dm/post", request.url.encodedPath)
            assertEquals("POST", request.method)
            val form = request.body as FormBody
            capture((0 until form.size).associate { form.name(it) to form.value(it) })
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":$code,"data":{"dmid":123}}""".toResponseBody()).build()
        }.build()

    @Test fun postsAllDanmakuFields() = runBlocking {
        var fields = emptyMap<String, String>()
        val ok = BilibiliDanmakuGateway(client(0) { fields = it })
            .sendDanmaku(99, "BVtest", "弹幕内容", 12345, 16711680, 4, "session", "csrf")
        assertTrue(ok)
        assertEquals("99", fields["oid"])
        assertEquals("1", fields["type"])
        assertEquals("弹幕内容", fields["msg"])
        assertEquals("BVtest", fields["bvid"])
        assertEquals("12345", fields["progress"])
        assertEquals("16711680", fields["color"])
        assertEquals("4", fields["mode"])
        assertEquals("csrf", fields["csrf"])
    }

    @Test fun nonZeroCodeReportsFailure() = runBlocking {
        val ok = BilibiliDanmakuGateway(client(-101) {})
            .sendDanmaku(99, "BVtest", "内容", 0, 16777215, 1, "session", "csrf")
        assertFalse(ok)
    }

    @Test fun rejectsBlankOverlongAndLoggedOut() = runBlocking {
        val gateway = BilibiliDanmakuGateway(client(0) {})
        assertThrows(IllegalArgumentException::class.java) { runBlocking { gateway.sendDanmaku(99, "BV", "", 0, 1, 1, "session", "csrf") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { gateway.sendDanmaku(99, "BV", "字".repeat(101), 0, 1, 1, "session", "csrf") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { gateway.sendDanmaku(99, "BV", "内容", 0, 1, 1, "", "csrf") } }
        Unit
    }
}
