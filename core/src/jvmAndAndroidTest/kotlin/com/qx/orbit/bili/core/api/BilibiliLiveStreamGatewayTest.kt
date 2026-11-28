package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliLiveStreamGatewayTest {

    private val playInfoJson = """
        {"code":0,"data":{"live_status":1,"is_portrait":true,"encrypted":false,"pwd_verified":true,
        "playurl_info":{"playurl":{
            "g_qn_desc":[{"qn":10000,"desc":"原画"},{"qn":250,"desc":"超清"}],
            "stream":[
                {"protocol_name":"http_stream","format":[{"format_name":"flv","codec":[
                    {"codec_name":"mp4","current_qn":10000,"accept_qn":[250],"base_url":"/http.flv","url_info":[{"host":"http://bad.example","extra":""}]}]}]},
                {"protocol_name":"https_stream","format":[{"format_name":"flv","codec":[
                    {"codec_name":"mp4","current_qn":10000,"accept_qn":[250,10000],"base_url":"/live-bvc/123.flv?","url_info":[{"host":"https://h.example","extra":"extra=1"}]}]}]}
            ]}}}}
    """.trimIndent()

    private fun gateway(json: String, onRequest: (okhttp3.Request) -> Unit = {}): BilibiliLiveGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("nav")) {
                """{"data":{"wbi_img":{"img_url":"https://test/${"a".repeat(32)}.png","sub_url":"https://test/${"b".repeat(32)}.png"}}}"""
            } else {
                onRequest(request)
                json
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        return BilibiliLiveGateway(client)
    }

    @Test fun parsesHttpsFlvStreamAndQualityList() = runBlocking {
        val stream = gateway(playInfoJson).loadStreamInfo(23058, 10000)
        assertEquals(1, stream.liveStatus)
        assertTrue(stream.isPortrait)
        assertEquals(listOf(10000, 250), stream.qualities.map { it.id })
        assertEquals(listOf("原画", "超清"), stream.qualities.map { it.label })
        assertEquals("https://h.example/live-bvc/123.flv?extra=1", stream.urlsByQuality[10000])
        assertEquals(stream.urlsByQuality[10000], stream.urlsByQuality[250])
    }

    @Test fun requestQualityFallsBackWithinAccepted() {
        val stream = CoreLiveStream(qualities = listOf(
            CorePlaybackQuality(10000, "原画"), CorePlaybackQuality(250, "超清"),
        ))
        assertEquals(250, stream.requestQuality(400))
        assertEquals(10000, stream.requestQuality(10000))
        assertEquals(250, stream.requestQuality(0))
    }

    @Test fun parsesRoomInfoAliases() = runBlocking {
        val room = gateway("""{"code":0,"data":{"room_id":0,"roomid":23058,"uid":9,"title":"直播标题","uname":"主播","online":42,"live_status":1,"area_v2_name":"音乐","user_cover":"//c.example/cover.jpg"}}""")
            .loadRoomInfo(23058)
        assertEquals(23058, room.roomId)
        assertEquals("音乐", room.areaName)
        assertEquals("https://c.example/cover.jpg", room.cover)
    }

    @Test fun parsesRoomListFromRecommend() = runBlocking {
        val rooms = gateway("""{"code":0,"data":{"list":[{"roomid":1,"uname":"a"},{"room_id":2,"uname":"b"}]}}""")
            .loadRecommend(1)
        assertEquals(listOf(1L, 2L), rooms.map { it.roomId })
    }
}
