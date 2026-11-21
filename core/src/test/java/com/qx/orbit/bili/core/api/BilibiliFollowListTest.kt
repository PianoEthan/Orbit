package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BilibiliFollowListTest {

    private fun gateway(capture: (okhttp3.Request) -> Unit = {}): BilibiliUserDataGateway {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            capture(request)
            val json = """{"code":0,"data":{"total":42,"list":[
                {"mid":1,"uname":"关注一","face":"//f/1.jpg","sign":"签名","follower":9},
                {"mid":0},
                {"mid":2,"uname":"关注二"}
            ]}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody()).build()
        }.build()
        return BilibiliUserDataGateway(client)
    }

    @Test fun followingListMapsUsersAndTotal() = runBlocking {
        var path: String? = null
        val page = gateway { path = it.url.encodedPath }.loadFollowingList(5, 2, "cookie")
        assertEquals("/x/relation/followings", path)
        assertEquals(42, page.total)
        assertEquals(listOf(1L, 2L), page.items.map { it.mid })
        assertEquals("https://f/1.jpg", page.items.first().face)
        assertEquals(9, page.items.first().fans)
    }

    @Test fun followerListUsesFollowersEndpoint() = runBlocking {
        var path: String? = null
        gateway { path = it.url.encodedPath }.loadFollowerList(5, 1, "cookie")
        assertEquals("/x/relation/followers", path)
    }
}
