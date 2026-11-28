package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliSearchTypeGatewayTest {

    private fun gateway(resultsJson: String, capture: (okhttp3.Request) -> Unit = {}): BilibiliSearchGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                capture(request)
                resultsJson
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        return BilibiliSearchGateway(client)
    }

    @Test fun usersParseStripsProtocolAndSign() = runBlocking {
        var type: String? = null
        val users = gateway("""{"code":0,"data":{"result":[{"mid":7,"uname":"UP主","usign":"<em>个人</em>简介","fans":1200,"upic":"//i0.hdslb.com/bfs/face/7.jpg"}]}}""") {
            type = it.url.queryParameter("search_type")
        }.searchUsers("关键词", 2)
        assertEquals("bili_user", type)
        val user = users.single()
        assertEquals(7, user.mid)
        assertEquals("个人简介", user.sign)
        assertEquals("https://i0.hdslb.com/bfs/face/7.jpg", user.face)
        assertEquals(1200, user.fans)
    }

    @Test fun roomsPreferRoomidThenShortId() = runBlocking {
        val rooms = gateway("""{"code":0,"data":{"result":[
            {"roomid":100,"short_id":20,"uid":3,"title":"<em>直播</em>间","uname":"a","cover_since":"x","online":7,"live_status":1},
            {"roomid":0,"short_id":30,"uname":"b"}
        ]}}""").searchRooms("直播", 1)
        assertEquals(listOf(100L, 30L), rooms.map { it.roomId })
        assertEquals("直播间", rooms.first().title)
    }

    @Test fun seasonsFallBackToMediaAndJoinAreaArrays() = runBlocking {
        val seasons = gateway("""{"code":0,"data":{"result":[
            {"season_id":0,"media_id":400,"id":41,"title":"<em>番</em>剧","cover":"//c/1.jpg","index_show":"更新至3话","areas":["日本","热血"]},
            {"season_id":0,"media_id":0,"id":0}
        ]}}""").searchSeasons("番", 1)
        val season = seasons.single()
        assertEquals(400, season.seasonId)
        assertEquals("番剧", season.title)
        assertEquals("日本/热血", season.areas)
        assertEquals("https://c/1.jpg", season.cover)
    }
}
