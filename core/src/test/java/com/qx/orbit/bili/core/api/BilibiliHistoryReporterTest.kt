package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliHistoryReporterTest {
    @Test fun reportedEpisodeAndPositionReturnThroughHistoryRead() = runBlocking {
        var savedCid = 0L
        var savedProgress = 0L
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.method == "POST") {
                val form = request.body as FormBody
                val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
                savedCid = fields.getValue("cid").toLong(); savedProgress = fields.getValue("progress").toLong()
                """{"code":0}"""
            } else """{"code":0,"data":{"list":[{"title":"视频","duration":100,"progress":$savedProgress,"history":{"oid":42,"bvid":"BV42","cid":$savedCid}}]}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        BilibiliHistoryReporter(client).report(42, 102, 45, "fake", "csrf")
        val item = BilibiliUserDataGateway(client).loadHistory("fake").single()
        assertEquals(102L, item.cid); assertEquals(45L, item.progressSeconds); assertEquals(0.45f, item.progressPercent)
    }

    @Test fun sendsAidCidSecondsAndAuthenticationWithFormEncoding() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            assertEquals("/x/v2/history/report", request.url.encodedPath)
            assertEquals("POST", request.method); assertEquals("fake-cookie", request.header("Cookie"))
            val body = request.body as FormBody
            val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
            assertEquals(mapOf("aid" to "42", "cid" to "101", "progress" to "45", "platform" to "android", "csrf" to "fake-csrf"), fields)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":0}""".toResponseBody()).build()
        }.build()
        BilibiliHistoryReporter(client).report(42, 101, 45, "fake-cookie", "fake-csrf")
        assertEquals(1, calls)
    }

    @Test fun rejectsInvalidTargetsMissingCredentialsAndServerRejection() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"code":-101}""".toResponseBody()).build()
        }.build()
        val reporter = BilibiliHistoryReporter(client)
        listOf(Triple(0L, 101L, 0L), Triple(42L, 0L, 0L), Triple(42L, 101L, -1L)).forEach { (aid, cid, progress) ->
            try { reporter.report(aid, cid, progress, "fake", "csrf"); fail() } catch (_: IllegalArgumentException) { }
        }
        try { reporter.report(42, 101, 0, "", ""); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
        try { reporter.report(42, 101, 0, "fake", "csrf"); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, calls)
    }
}
