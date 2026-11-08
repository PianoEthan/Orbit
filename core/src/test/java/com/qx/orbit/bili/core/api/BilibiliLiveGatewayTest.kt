package com.qx.orbit.bili.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BilibiliLiveGatewayTest {
    @Test fun publicReplyThreadReturnsRootAndChildren() = runBlocking {
        assumeTrue(System.getenv("ORBIT_LIVE_API_TEST") == "1")
        val gateway = BilibiliVideoGateway()
        val root = gateway.loadReplyPage(170001).replies.first { it.replyCount > 0 }
        val page = gateway.loadReplyThread(170001, root.rpid, 1, "")
        assertEquals(root.rpid, page.root?.rpid)
        assertTrue(page.replies.isNotEmpty())
    }

    @Test fun publicCommentsLoadTwoDifferentPages() = runBlocking {
        assumeTrue(System.getenv("ORBIT_LIVE_API_TEST") == "1")
        val gateway = BilibiliVideoGateway()
        val first = gateway.loadReplyPage(170001)
        assertTrue(first.replies.isNotEmpty())
        val offset = requireNotNull(first.nextOffset)
        val second = gateway.loadReplyPage(170001, offset)
        assertTrue(second.replies.isNotEmpty())
        assertTrue(second.replies.any { next -> first.replies.none { it.rpid == next.rpid } })
    }

    @Test fun dashVideoAndAudioSupportMediaReads() = runBlocking {
        assumeTrue(System.getenv("ORBIT_LIVE_API_TEST") == "1")
        val details = BilibiliVideoGateway().loadDetails(CoreVideo(0, "BV1Kb411W75N", "", "", ""))
        val playback = BilibiliPlaybackGateway().resolve(details.video)
        assertNotNull(playback.audioUrl)
        val tracks = listOf(listOf(playback.urls.first()) + playback.videoBackups, listOf(requireNotNull(playback.audioUrl)) + playback.audioBackups)
        val client = OkHttpClient()
        for ((index, addresses) in tracks.withIndex()) {
            var readable = false
            for (address in addresses.distinct()) {
                val request = Request.Builder().url(address)
                    .header("Referer", "https://www.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                    .header("Range", "bytes=0-1023").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body.byteStream().read() >= 0) readable = true
                }
                if (readable) break
            }
            assertTrue("Track $index must be readable", readable)
        }
    }

    @Test fun publicVideoFlowReturnsReadableMedia() = runBlocking {
        assumeTrue(System.getenv("ORBIT_LIVE_API_TEST") == "1")
        val feeds = BilibiliFeedGateway()
        val recommended = feeds.loadFeed(CoreFeed.RECOMMEND, 1)
        val popular = feeds.loadFeed(CoreFeed.POPULAR, 1)
        assertTrue(recommended.isNotEmpty())
        assertTrue(popular.isNotEmpty())
        val search = BilibiliSearchGateway().searchVideos("Android", 1)
        assertTrue(search.isNotEmpty())
        val details = BilibiliVideoGateway().loadDetails(popular.first())
        assertTrue(details.video.cid > 0)
        val playback = BilibiliPlaybackGateway().resolve(details.video)
        assertTrue(playback.urls.isNotEmpty())
        val request = Request.Builder().url(playback.urls.first())
            .header("Referer", "https://www.bilibili.com/")
            .header("Range", "bytes=0-1023").build()
        OkHttpClient().newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
            assertTrue(response.body.byteStream().read() >= 0)
        }
    }
}
