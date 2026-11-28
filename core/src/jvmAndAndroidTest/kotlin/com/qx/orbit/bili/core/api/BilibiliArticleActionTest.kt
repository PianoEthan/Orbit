package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliArticleActionTest {
    private fun client(code: Int, capture: (String, Map<String, String>) -> Unit) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as FormBody
            capture(request.url.encodedPath, (0 until form.size).associate { form.name(it) to form.value(it) })
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":$code}""".toResponseBody()).build()
        }.build()

    @Test fun likePostsTypeOneAndCancelTypeTwo() = runBlocking {
        var path = ""
        var fields = emptyMap<String, String>()
        val gateway = BilibiliArticleGateway(client(0) { p, f -> path = p; fields = f })
        assertTrue(gateway.likeArticle(2, true, "session", "csrf"))
        assertEquals("/x/article/like", path)
        assertEquals("2", fields["id"])
        assertEquals("1", fields["type"])
        gateway.likeArticle(2, false, "session", "csrf")
        assertEquals("2", fields["type"])
    }

    @Test fun favoriteTogglesAddAndDelPath() = runBlocking {
        var path = ""
        val gateway = BilibiliArticleGateway(client(0) { p, _ -> path = p })
        assertTrue(gateway.favoriteArticle(2, true, "session", "csrf"))
        assertEquals("/x/article/favorites/add", path)
        gateway.favoriteArticle(2, false, "session", "csrf")
        assertEquals("/x/article/favorites/del", path)
    }

    @Test fun coinPostsCvAsAidWithUpidAndMultiply() = runBlocking {
        var path = ""
        var fields = emptyMap<String, String>()
        val ok = BilibiliArticleGateway(client(0) { p, f -> path = p; fields = f })
            .coinArticle(2, 7, 2, "session", "csrf")
        assertTrue(ok)
        assertEquals("/x/web-interface/coin/add", path)
        assertEquals("2", fields["aid"])
        assertEquals("7", fields["upid"])
        assertEquals("2", fields["multiply"])
    }

    @Test fun nonZeroCodeAndLoggedOutRejected() = runBlocking {
        val gateway = BilibiliArticleGateway(client(-101) { _, _ -> })
        assertFalse(gateway.likeArticle(2, true, "session", "csrf"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { gateway.coinArticle(2, 7, 1, "", "csrf") } }
        Unit
    }
}
