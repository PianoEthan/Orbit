package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliAuthGatewayTest {
    @Test fun generatesQrCodeAndPollsSuccess() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.encodedPath
            val (code, json, headers) = when {
                url.endsWith("generate") -> Triple(200, """{"code":0,"data":{"url":"https://passport.test/scan","qrcode_key":"key123"}}""", emptyList())
                url.endsWith("poll") -> Triple(200, """{"code":0,"data":{"code":0,"message":"成功","refresh_token":"rf123"}}""", listOf("DedeUserID=12345; Path=/", "SESSDATA=sess; Path=/"))
                url.endsWith("nav") -> Triple(200, """{"code":0,"data":{"mid":12345,"uname":"OrbitUser","face":"http://face.test/1.jpg","money":15.0,"isLogin":true,"level_info":{"current_level":6}}}""", emptyList())
                else -> Triple(404, "{}", emptyList())
            }
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("OK")
                .body(json.toResponseBody())
            headers.forEach { builder.addHeader("Set-Cookie", it) }
            builder.build()
        }.build()

        val gateway = BilibiliAuthGateway(client)
        val qr = gateway.generateQrCode()
        assertEquals("https://passport.test/scan", qr.url)
        assertEquals("key123", qr.qrcodeKey)

        val poll = gateway.pollQrCode("key123")
        assertEquals(CoreQrPollStatus.SUCCESS, poll.status)
        assertEquals(12345L, poll.mid)
        assertTrue(poll.cookies.contains("DedeUserID=12345"))

        val profile = gateway.getMyProfile(poll.cookies)
        assertTrue(profile.isLogin)
        assertEquals("OrbitUser", profile.uname)
        assertEquals(12345L, profile.mid)
        assertEquals("https://face.test/1.jpg", profile.face)
        assertEquals(6, profile.level)
    }

    @Test fun rejectsMalformedFailedAndCredentiallessSuccessResponses() = runBlocking {
        val responses = listOf(
            "{}", "{\"code\":0}", "{\"code\":0,\"data\":{}}",
            "{\"code\":-101,\"data\":{\"code\":0}}", "{\"code\":0,\"data\":{\"code\":0}}",
        )
        for (json in responses) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(json.toResponseBody()).build()
            }.build()
            assertEquals(CoreQrPollStatus.ERROR, BilibiliAuthGateway(client).pollQrCode("key").status)
        }
    }

    @Test fun mapsWaitingScannedAndExpiredStates() = runBlocking {
        val statuses = mapOf(86101 to CoreQrPollStatus.WAITING_SCAN, 86090 to CoreQrPollStatus.SCANNED_WAITING_CONFIRM, 86038 to CoreQrPollStatus.EXPIRED)
        for ((code, expected) in statuses) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"code":0,"data":{"code":$code}}""".toResponseBody()).build()
            }.build()
            assertEquals(expected, BilibiliAuthGateway(client).pollQrCode("key").status)
        }
    }

    @Test fun profileOnlyTreatsExplicitUnauthenticatedResponseAsExpired() = runBlocking {
        val responses = ArrayDeque(listOf(
            200 to """{"code":-101}""", 200 to """{"code":0,"data":{"isLogin":false}}""",
            200 to """{"code":-412}""", 503 to """{"code":-101}""",
            200 to """{"code":0,"data":{}}""", 200 to """{"code":0,"data":{"isLogin":true,"mid":0}}""",
        ))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (status, json) = responses.removeFirst()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("response").body(json.toResponseBody()).build()
        }.build()
        val gateway = BilibiliAuthGateway(client)
        assertEquals(false, gateway.getMyProfile("fake").isLogin)
        assertEquals(false, gateway.getMyProfile("fake").isLogin)
        repeat(4) {
            try { gateway.getMyProfile("fake"); throw AssertionError("Transient or malformed response must not erase session") } catch (_: IllegalStateException) { }
        }
    }

    @Test fun parseCookieValueExtractsCorrectKey() {
        val cookie = "DedeUserID=9876; bili_jct=csrf123; SESSDATA=sess456"
        assertEquals("9876", parseCookieValue(cookie, "DedeUserID"))
        assertEquals("csrf123", parseCookieValue(cookie, "bili_jct"))
        assertEquals("sess456", parseCookieValue(cookie, "SESSDATA"))
        assertEquals("", parseCookieValue(cookie, "nonexistent"))
    }
}
