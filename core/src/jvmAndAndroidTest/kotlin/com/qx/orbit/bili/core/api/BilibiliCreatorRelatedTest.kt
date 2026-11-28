package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliCreatorRelatedTest {
    private fun client(body: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body(chain.request()).toResponseBody()).build()
    }.build()

    @Test fun creatorUsesCurrentCookieAndNormalizesAvatar() = runBlocking {
        val gateway = BilibiliCreatorGateway(client { request ->
            assertEquals("SESSDATA=test", request.header("Cookie"))
            assertEquals("123", request.url.queryParameter("mid"))
            """{"code":0,"data":{"following":true,"card":{"name":"UP 主","face":"//img.test/avatar","sign":"签名","fans":50,"attention":10}}}"""
        })
        val creator = gateway.loadCreator(123, "SESSDATA=test")
        assertTrue(creator.followed)
        assertEquals("https://img.test/avatar", creator.avatar)
        assertEquals(50L, creator.fans)
    }

    @Test fun relatedAndCollectionAreValidVideoDestinations() = runBlocking {
        val archive = """{"aid":2,"bvid":"BV2","cid":102,"title":"合集视频","pic":"//img.test/cover","duration":90,"owner":{"mid":123,"name":"UP 主"}}"""
        val client = client { request ->
            if (request.url.encodedPath.endsWith("related")) """{"code":0,"data":[$archive,{"bvid":""}]}"""
            else """{"code":0,"data":{"aid":1,"bvid":"BV1","owner":{"mid":123,"name":"UP 主","face":"//img.test/avatar"},"ugc_season":{"title":"系列合集","sections":[{"episodes":[{"arc":$archive}]}]}}}"""
        }
        val related = BilibiliRelatedGateway(client).loadRelated("BV1", "")
        assertEquals(listOf("BV2"), related.map { it.bvid })
        val detail = BilibiliVideoGateway(client).loadDetails(CoreVideo(1, "BV1", "", "", ""))
        assertEquals(123L, detail.ownerMid)
        assertEquals("系列合集", detail.collectionTitle)
        assertEquals("BV2", detail.collectionVideos.single().bvid)
        assertEquals(102L, detail.collectionVideos.single().cid)
    }

    @Test fun relationWriteIsNotRetriedAndRequiresLogin() = runBlocking {
        var calls = 0
        val gateway = BilibiliCreatorGateway(client { request ->
            calls++
            assertEquals("POST", request.method)
            """{"code":0}"""
        })
        try { gateway.changeRelation(123, 1, "", ""); fail("Must require login") } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls)
        gateway.changeRelation(123, 1, "SESSDATA=test", "csrf")
        assertEquals(1, calls)
    }
}
