package com.qx.orbit.bili.core.api

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliDanmakuGatewayTest {
    private val xml = "<i><d p=\"1,1,25,16777215\">弹幕测试</d></i>".toByteArray()

    @Test fun supportsPlainRawDeflateZlibAndGzip() {
        val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(xml) } }.toByteArray()
        val compressed = listOf(true, false).map { raw ->
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, raw)
            try {
                ByteArrayOutputStream().also { out -> DeflaterOutputStream(out, deflater).use { it.write(xml) } }.toByteArray()
            } finally { deflater.end() }
        }
        (listOf(xml, gzip) + compressed).forEach { bytes ->
            assertArrayEquals(xml, decompressBilibiliDanmakuStream(bytes).use { it.readBytes() })
        }
    }

    @Test fun requestUsesSelectedCidAndSessionAndClosesResponse() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("102", chain.request().url.queryParameter("oid"))
            assertEquals("SESSDATA=test", chain.request().header("Cookie"))
            assertEquals("https://www.bilibili.com/", chain.request().header("Referer"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(xml.toResponseBody()).build()
        }.build()
        assertArrayEquals(xml, BilibiliDanmakuGateway(client) { "SESSDATA=test" }.loadXmlDanmaku(102))
    }

    @Test fun invalidCidAndHttpFailureDoNotInterruptPlayback() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body("offline".toResponseBody()).build()
        }.build()
        val gateway = BilibiliDanmakuGateway(client)
        assertNull(gateway.loadXmlDanmaku(0))
        assertEquals(0, calls)
        assertNull(gateway.loadXmlDanmaku(101))
    }
}
