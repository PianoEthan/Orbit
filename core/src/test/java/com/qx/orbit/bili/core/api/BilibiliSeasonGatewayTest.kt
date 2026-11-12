package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliSeasonGatewayTest {
    private fun client(body: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body(chain.request()).toResponseBody()).build()
    }.build()

    @Test fun seasonKeepsEpisodeIdentitySeasonsAndResumeProgress() = runBlocking {
        val gateway = BilibiliSeasonGateway(client { request ->
            assertEquals("42", request.url.queryParameter("season_id"))
            assertEquals("SESSDATA=test", request.header("Cookie"))
            """{"code":0,"result":{"season_id":42,"media_id":43,"title":"测试番剧","cover":"//img.test/poster","evaluate":"简介","user_status":{"follow":1,"progress":{"last_ep_id":12,"last_time":25}},"episodes":[{"id":11,"aid":1,"cid":101,"bvid":"BV1","title":"1","long_title":"第一集"},{"id":12,"aid":2,"cid":102,"bvid":"BV2","title":"2","long_title":"第二集"}],"section":[{"episodes":[{"id":13,"aid":3,"cid":103,"title":"花絮"}]}],"seasons":[{"season_id":42,"season_title":"第一季"},{"season_id":44,"season_title":"第二季"}]}}"""
        })
        val detail = gateway.loadSeason(42, "SESSDATA=test")
        assertTrue(detail.followed)
        assertEquals("https://img.test/poster", detail.season.cover)
        assertEquals(listOf(11L, 12L, 13L), detail.episodes.map { it.id })
        assertEquals(42L, detail.episodes[1].video.seasonId)
        assertEquals(12L, detail.episodes[1].video.episodeId)
        assertEquals(25000L, detail.episodes[1].video.startPositionMs)
        assertEquals(listOf(42L, 44L), detail.seasons.map { it.id })
    }

    @Test fun catalogAndFollowedListsUseDifferentTypesAndPagination() = runBlocking {
        val gateway = BilibiliSeasonGateway(client { request ->
            if (request.url.encodedPath.endsWith("result")) {
                assertEquals("5", request.url.queryParameter("season_type"))
                assertEquals("2", request.url.queryParameter("page"))
                """{"code":0,"data":{"has_next":1,"list":[{"season_id":42,"title":"电视剧"}]}}"""
            } else {
                assertEquals("2", request.url.queryParameter("type"))
                assertEquals("123", request.url.queryParameter("vmid"))
                """{"code":0,"data":{"total":21,"list":[{"season_id":42,"title":"电视剧"}]}}"""
            }
        })
        assertTrue(gateway.loadCatalog(5, 2, "").hasMore)
        assertTrue(gateway.loadFollowed(123, 2, 1, "SESSDATA=test").hasMore)
        assertFalse(gateway.loadFollowed(123, 2, 2, "SESSDATA=test").hasMore)
    }

    @Test fun pgcPlaybackUsesEpisodeEndpointAndPreservesActualQuality() = runBlocking {
        val video = CoreVideo(2, "BV2", "第二集", "", "番剧", cid = 102, episodeId = 12, seasonId = 42)
        val client = client { request ->
            assertEquals("/pgc/player/web/playurl", request.url.encodedPath)
            assertEquals("12", request.url.queryParameter("ep_id"))
            assertEquals("102", request.url.queryParameter("cid"))
            assertEquals("2", request.url.queryParameter("avid"))
            assertTrue(request.url.queryParameter("fnval") in listOf("1", "144"))
            assertEquals("SESSDATA=test", request.header("Cookie"))
            """{"code":0,"result":{"quality":64,"accept_quality":[80,64],"accept_description":["1080P","720P"],"durl":[{"url":"https://cdn.test/ep12.mp4","length":90000,"backup_url":["https://backup.test/ep12.mp4"]}]}}"""
        }
        val result = BilibiliPlaybackGateway(client, cookieProvider = { "SESSDATA=test" }).resolveStream(video, 80, true)
        assertEquals(video, result.video)
        assertEquals(64, result.quality)
        assertEquals(listOf("https://backup.test/ep12.mp4"), result.videoBackups)
    }

    @Test fun followAndHistorySendCorrectEpisodeContextWithoutRealAccountWrites() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { requests += it; """{"code":0}""" }
        BilibiliSeasonGateway(client).follow(42, true, "SESSDATA=test", "csrf")
        BilibiliSeasonGateway(client).follow(42, false, "SESSDATA=test", "csrf")
        val video = CoreVideo(2, "BV2", "", "", "", cid = 102, episodeId = 12, seasonId = 42)
        BilibiliHistoryReporter(client).reportEpisode(video, 25, "SESSDATA=test", "csrf")
        assertEquals("/pgc/web/follow/add", requests[0].url.encodedPath)
        assertEquals("/pgc/web/follow/del", requests[1].url.encodedPath)
        val form = requests.last().body as FormBody
        val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
        assertEquals("12", fields["epid"])
        assertEquals("42", fields["sid"])
        assertEquals("4", fields["type"])
        assertEquals("25", fields["played_time"])
    }
}
