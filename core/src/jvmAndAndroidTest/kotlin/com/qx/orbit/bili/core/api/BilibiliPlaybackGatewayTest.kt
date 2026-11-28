package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliPlaybackGatewayTest {
    private val video = CoreVideo(1, "BVtest", "标题", "https://cover.test/a.jpg", "UP")
    private val nav = """{"code":-101,"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""

    @Test
    fun resolvesCidAndAllSegmentsWithSignedRequest() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val json = when (request.url.encodedPath) {
                "/x/web-interface/nav" -> nav
                "/x/player/wbi/playurl" -> """{"code":0,"data":{"durl":[{"url":"https://cdn.test/1.mp4","length":20000,"backup_url":["https://backup.test/1.mp4"]},{"url":"https://cdn.test/2.mp4","length":30000,"backup_url":["https://backup.test/2.mp4"]}]}}"""
                else -> error("Unexpected path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        var detailCalls = 0
        val details = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails {
                detailCalls++
                return CoreVideoDetails(video.copy(cid = 99), "", 0, 0, 0, 0)
            }
        }
        val result = BilibiliPlaybackGateway(client, details).resolve(video)
        assertEquals(99L, result.video.cid)
        assertEquals(1, detailCalls)
        assertEquals(2, result.urls.size)
        assertEquals(listOf(20000L, 30000L), result.segmentDurationsMs)
        assertEquals(listOf(listOf("https://backup.test/1.mp4"), listOf("https://backup.test/2.mp4")), result.segmentBackups)
        assertEquals(listOf("https://backup.test/1.mp4"), result.videoBackups)
        val query = requests.last().url
        assertEquals("99", query.queryParameter("cid"))
        assertEquals("144", query.queryParameter("fnval"))
        assertEquals("1", query.queryParameter("try_look"))
        assertEquals(32, query.queryParameter("w_rid")!!.length)
    }

    @Test
    fun loggedInSessionOmitsTryLook() = runBlocking {
        var tryLook: String? = "unset"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                tryLook = request.url.queryParameter("try_look")
                """{"code":0,"data":{"durl":[{"url":"https://cdn.test/1.mp4"}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val details = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails =
                CoreVideoDetails(video.copy(cid = 99), "", 0, 0, 0, 0)
        }
        BilibiliPlaybackGateway(client, details, cookieProvider = { "SESSDATA=fake" }).resolve(video)
        assertNull(tryLook)
    }

    @Test
    fun usesSelectedPartCidWithoutReplacingItWithFirstPart() = runBlocking {
        var requestedCid: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                requestedCid = request.url.queryParameter("cid")
                """{"code":0,"data":{"durl":[{"url":"https://cdn.test/part2.mp4"}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val details = object : VideoGateway {
            override suspend fun loadDetails(video: CoreVideo): CoreVideoDetails = error("Must preserve selected CID")
        }
        val result = BilibiliPlaybackGateway(client, details).resolve(video.copy(cid = 1002))
        assertEquals("1002", requestedCid)
        assertEquals(1002L, result.video.cid)
    }

    @Test
    fun qualityRequestCarriesSessionAndReportsActualDowngrade() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                assertEquals("80", request.url.queryParameter("qn"))
                assertEquals("1", request.url.queryParameter("high_quality"))
                assertEquals("test-session", request.header("Cookie"))
                """{"code":0,"data":{"quality":32,"accept_quality":[80,32],"accept_description":["1080P","480P"],"durl":[{"url":"https://cdn.test/480.mp4"}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliPlaybackGateway(client, cookieProvider = { "test-session" }).resolveQuality(video.copy(cid = 99), 80)
        assertEquals(32, result.quality)
        assertEquals(listOf(CorePlaybackQuality(80, "1080P"), CorePlaybackQuality(32, "480P")), result.qualities)
    }

    @Test
    fun dashSelectsRequestedAvcAndMergesSeparateAudio() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                assertEquals("144", request.url.queryParameter("fnval"))
                assertEquals("1", request.url.queryParameter("fourk"))
                """{"code":0,"data":{"quality":80,"dash":{"video":[{"id":80,"codecs":"hev1","baseUrl":"https://cdn.test/hevc.m4s"},{"id":80,"codecs":"avc1.640032","base_url":"http://cdn.test/1080.m4s","backupUrl":["https://backup.test/1080.m4s"]},{"id":32,"codecid":7,"baseUrl":"https://cdn.test/480.m4s"}],"audio":[{"codecs":"mp4a.40.2","bandwidth":64000,"baseUrl":"https://cdn.test/low.m4s"},{"codecs":"mp4a.40.2","bandwidth":192000,"base_url":"https://cdn.test/audio.m4s","backupUrl":["https://backup.test/audio.m4s"]}]}}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliPlaybackGateway(client).resolveQuality(video.copy(cid = 99), 80)
        assertEquals(80, result.quality)
        assertEquals(listOf("https://cdn.test/1080.m4s"), result.urls)
        assertEquals("https://cdn.test/audio.m4s", result.audioUrl)
        assertEquals(listOf("https://backup.test/1080.m4s"), result.videoBackups)
        assertEquals(listOf("https://backup.test/audio.m4s"), result.audioBackups)
        val downgraded = BilibiliPlaybackGateway(client).resolveQuality(video.copy(cid = 99), 64)
        assertEquals(32, downgraded.quality)
        assertEquals(listOf("https://cdn.test/480.m4s"), downgraded.urls)
    }

    @Test fun progressiveStreamKeeps1080pRequestAndAudioVideoCombined() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) nav else {
                assertTrue(request.url.queryParameter("fnval") in listOf("1", "144"))
                if (request.url.queryParameter("fnval") == "1") assertEquals("html5", request.url.queryParameter("platform"))
                assertEquals("80", request.url.queryParameter("qn"))
                """{"code":0,"data":{"quality":80,"durl":[{"url":"https://cdn.test/1080.mp4","length":120000,"backup_url":["https://backup.test/1080.mp4"]}]}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val result = BilibiliPlaybackGateway(client).resolveStream(video.copy(cid = 101), 80, true)
        assertEquals(80, result.quality)
        assertNull(result.audioUrl)
        assertEquals(listOf(120000L), result.segmentDurationsMs)
        assertEquals(listOf("https://backup.test/1080.mp4"), result.videoBackups)
    }

    @Test
    fun rejectsPermissionFailureWithoutReturningPlayback() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((if (chain.request().url.encodedPath.endsWith("nav")) nav else
                    """{"code":-403,"message":"需要登录"}""").toResponseBody()).build()
        }.build()
        try {
            BilibiliPlaybackGateway(client).resolve(video.copy(cid = 99))
            fail("Expected permission failure")
        } catch (expected: IllegalStateException) {
            assertEquals("需要登录", expected.message)
        }
    }
}
