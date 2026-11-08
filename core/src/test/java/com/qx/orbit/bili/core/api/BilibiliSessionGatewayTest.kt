package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliSessionGatewayTest {

    @Test
    fun mergeCookieStringCombinesAndDeduplicatesKeys() {
        val original = "SESSDATA=abc; DedeUserID=123; old=1"
        val merged = mergeCookieString(original, mapOf("buvid3" to "b3_val", "buvid4" to "b4_val", "old" to "2"))
        assertEquals("SESSDATA=abc; DedeUserID=123; old=2; buvid3=b3_val; buvid4=b4_val", merged)
    }

    @Test
    fun initializeSessionFetchesBuvidsFromSpiAndSavesCookie() = runBlocking {
        var requestedUrl = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requestedUrl = request.url.toString()
            val json = """{"code":0,"message":"ok","data":{"b_3":"buvid3_test","b_4":"buvid4_test"}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()

        var currentCookie = "SESSDATA=user_sess; DedeUserID=999"
        var savedCookie = ""
        val gateway = BilibiliSessionGateway(
            client = client,
            cookieProvider = { currentCookie },
            cookieSaver = { savedCookie = it },
        )

        gateway.initializeSession()

        assertTrue(requestedUrl.contains("finger/spi"))
        assertEquals("SESSDATA=user_sess; DedeUserID=999; buvid3=buvid3_test; buvid4=buvid4_test", savedCookie)
        assertEquals("buvid3_test", BilibiliDeviceFingerprint.current()["buvid3"])
    }

    @Test
    fun initializeSessionSkipsWhenBuvidAlreadyPresent() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val json = """{"code":0,"data":{"b_3":"new_b3"}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()

        val gateway = BilibiliSessionGateway(
            client = client,
            cookieProvider = { "SESSDATA=test; buvid3=already_exist" },
            cookieSaver = { fail("Should not save") },
        )

        gateway.initializeSession()
        assertEquals(0, calls)
    }

    @Test
    fun fallbackBuvidWorksWhenSpiFails() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val json = if (url.contains("finger/spi")) {
                """{"code":-400,"message":"error"}"""
            } else {
                """{"code":0,"data":{"buvid":"fallback_b3"}}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()

        var saved = ""
        val gateway = BilibiliSessionGateway(
            client = client,
            cookieProvider = { "SESSDATA=test" },
            cookieSaver = { saved = it },
        )

        gateway.initializeSession()
        assertTrue(saved.contains("buvid3=fallback_b3"))
    }
}
