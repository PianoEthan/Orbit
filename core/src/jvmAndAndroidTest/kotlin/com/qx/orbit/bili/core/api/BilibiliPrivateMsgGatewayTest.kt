package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliPrivateMsgGatewayTest {

    private fun gateway(responder: (okhttp3.Request) -> String): BilibiliPrivateMsgGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(responder(request).toResponseBody()).build()
        }.build()
        return BilibiliPrivateMsgGateway(client)
    }

    @Test fun sessionsSkipAccountInfoAndParseNestedContent() = runBlocking {
        val sessions = gateway {
            """{"code":0,"data":{"session_list":[
                {"talker_id":5,"unread_count":2,"session_type":1,"last_msg":{"msg_type":1,"timestamp":170,"msg_seqno":9,"content":"{\"content\":\"你好\"}"}},
                {"talker_id":6,"account_info":{"name":"官方"},"last_msg":null},
                {"talker_id":0,"last_msg":null}
            ]}}"""
        }.loadSessions(20, "cookie")
        val session = sessions.single()
        assertEquals(5L, session.talkerId)
        assertEquals(2, session.unread)
        assertEquals("你好", session.lastMessage?.content)
        assertEquals(9L, session.lastMessage?.seqno)
    }

    @Test fun historyMarksNonTextMessages() = runBlocking {
        var path: String? = null
        val page = gateway { request ->
            path = request.url.encodedPath
            """{"code":0,"data":{"has_more":1,"messages":[
                {"sender_uid":5,"msg_type":1,"timestamp":1,"msg_seqno":2,"content":"{\"content\":\"文字\"}"},
                {"sender_uid":7,"msg_type":2,"timestamp":3,"msg_seqno":4,"content":"{\"url\":\"https://img\"}"}
            ]}}"""
        }.loadHistory(5, 20, 0, 0, "cookie")
        assertTrue(path!!.endsWith("fetch_session_msgs"))
        assertTrue(page.hasMore)
        assertEquals(listOf("文字", "[图片]"), page.messages.map { it.content })
    }

    @Test fun userCardsSupportBothShapes() = runBlocking {
        val flat = gateway { """{"code":0,"data":[{"mid":8,"name":"丁","face":"https://f/8.jpg"}]}""" }
            .loadUsers(listOf(8), "cookie")
        assertEquals("丁", flat.single().name)
        val nested = gateway { """{"code":0,"data":{"cards":[{"mid":9,"name":"戊","face":"https://f/9.jpg"}]}}""" }
            .loadUsers(listOf(9), "cookie")
        assertEquals(9L, nested.single().mid)
    }

    @Test fun emptyUidListSkipsNetwork() = runBlocking {
        val gateway = gateway { error("must not request") }
        assertTrue(gateway.loadUsers(emptyList(), "cookie").isEmpty())
    }
}
