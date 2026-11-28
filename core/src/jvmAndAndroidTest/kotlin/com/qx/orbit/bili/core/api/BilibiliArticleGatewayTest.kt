package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliArticleGatewayTest {
    private fun client(handler: (okhttp3.Request) -> String) =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(handler(request).toResponseBody()).build()
        }.build()

    @Test fun articleParsesStructuredParagraphs() = runBlocking {
        val json = """{"code":0,"data":{"title":"标题","banner_url":"//pic.test/banner","ctime":1700000000,
            "author":{"mid":7,"name":"作者","face":"//face.test/a"},
            "stats":{"view":100,"like":10,"reply":2,"coin":3,"favorite":5},
            "opus":{"content":{"paragraphs":[
                {"para_type":1,"text":{"nodes":[{"word":{"words":"你好"}},{"rich":{"text":"世界"}}]}},
                {"para_type":8,"text":{"nodes":[{"word":{"words":"小标题"}}]}},
                {"para_type":4,"text":{"nodes":[{"word":{"words":"引用"}}]}},
                {"para_type":2,"pic":{"pics":[{"url":"//pic.test/1"}]}},
                {"para_type":3}
            ]}}}}"""
        val article = BilibiliArticleGateway(client { json }).loadArticle(2, "")
        assertEquals("标题", article.title)
        assertEquals("作者", article.author)
        assertEquals(7L, article.authorMid)
        assertEquals("https://face.test/a", article.avatar)
        assertEquals("https://pic.test/banner", article.banner)
        assertEquals(100L, article.stats.view)
        assertEquals(5L, article.stats.favorite)
        assertEquals(5, article.blocks.size)
        assertEquals(CoreArticleBlock.Text("你好世界", CoreArticleTextKind.PARAGRAPH), article.blocks[0])
        assertEquals(CoreArticleBlock.Text("小标题", CoreArticleTextKind.HEADING), article.blocks[1])
        assertEquals(CoreArticleBlock.Text("引用", CoreArticleTextKind.QUOTE), article.blocks[2])
        assertEquals(CoreArticleBlock.Image("https://pic.test/1"), article.blocks[3])
        assertEquals(CoreArticleBlock.Divider, article.blocks[4])
    }

    @Test fun articleFallsBackToHtmlParsing() = runBlocking {
        val json = """{"code":0,"data":{"title":"T","content":"<p>段落</p><img src=\"//pic.test/x\"/><h2>标题</h2><blockquote>引</blockquote><hr>"}}"""
        val article = BilibiliArticleGateway(client { json }).loadArticle(2, "")
        assertEquals(CoreArticleBlock.Text("段落"), article.blocks[0])
        assertEquals(CoreArticleBlock.Image("https://pic.test/x"), article.blocks[1])
        assertEquals(CoreArticleBlock.Text("标题", CoreArticleTextKind.HEADING), article.blocks[2])
        assertEquals(CoreArticleBlock.Text("引", CoreArticleTextKind.QUOTE), article.blocks[3])
        assertEquals(CoreArticleBlock.Divider, article.blocks[4])
    }

    @Test fun opusExtractsInitialStateFromHtml() = runBlocking {
        val state = """{"opus":{"title":"图文标题","content":{"paragraphs":[{"para_type":1,"text":{"nodes":[{"word":{"words":"正文"}}]}}]}},
            "detail":{"id_str":"99","modules":{"module_author":{"name":"UP","mid":8,"face":"//face.test/b","pub_ts":1700000000},
                "module_stat":{"like":{"count":9},"comment":{"count":4},"favorite":{"count":6}}}}}"""
        val html = "<html><script>window.__INITIAL_STATE__=$state;(function(){})()</script></html>"
        val article = BilibiliArticleGateway(client { req ->
            assertEquals("/opus/99", req.url.encodedPath)
            html
        }).loadOpus("99", "")
        assertEquals("图文标题", article.title)
        assertEquals("UP", article.author)
        assertEquals(8L, article.authorMid)
        assertEquals(9L, article.stats.like)
        assertEquals(4L, article.stats.reply)
        assertEquals(6L, article.stats.favorite)
        assertEquals(CoreArticleBlock.Text("正文"), article.blocks.single())
    }

    @Test fun opusWithoutMarkerThrows() = runBlocking {
        val gateway = BilibiliArticleGateway(client { "<html>no state</html>" })
        assertThrows(IllegalStateException::class.java) { runBlocking { gateway.loadOpus("99", "") } }
        Unit
    }
}
