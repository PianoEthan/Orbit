package com.qx.orbit.bili.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BilibiliFeedGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .build(),
    private val cookieProvider: () -> String = { "" },
) : FeedGateway {
    override suspend fun loadFeed(feed: CoreFeed, page: Int): List<CoreVideo> = withContext(Dispatchers.Default) {
        loadFeedData(feed, page)
    }

    private suspend fun loadFeedData(feed: CoreFeed, page: Int): List<CoreVideo> {
        require(page > 0)
        val url = when (feed) {
            CoreFeed.RECOMMEND -> client.signedBilibiliUrl(
                "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd",
                mapOf(
                    "fresh_idx" to page.toString(),
                    "fresh_idx_1h" to page.toString(),
                    "brush" to page.toString(),
                    "fresh_type" to "4",
                    "feed_version" to "V8",
                    "web_location" to "1430650",
                    "ps" to "20",
                ),
            )
            CoreFeed.POPULAR -> "https://api.bilibili.com/x/web-interface/popular".toHttpUrl().newBuilder()
                .addQueryParameter("pn", page.toString()).addQueryParameter("ps", "20").build().toString()
        }

        val effectiveCookie = BilibiliDeviceFingerprint.attachIfMissing(cookieProvider())
        val data = client.getBilibiliJson(url, cookie = effectiveCookie).requireBilibiliData()
        val items = data.getAsJsonArray("item") ?: data.getAsJsonArray("list") ?: return emptyList()
        return items.mapNotNull { it.asJsonObject.toCoreVideo() }
    }

    override suspend fun removeVideo(video: CoreVideo) = Unit

    private fun JsonObject.toCoreVideo(): CoreVideo? {
        val gotoType = string("goto")
        if (gotoType != null && gotoType != "av") return null
        val bvid = string("bvid")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = long("aid") ?: long("id") ?: return null
        if (id <= 0L) return null
        val owner = getAsJsonObject("owner")
        val title = string("title")?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
        val cover = normalizeUrl(string("pic"))
        if (title.isBlank() || cover.isBlank()) return null
        val stat = getAsJsonObject("stat")
        val durationSec = get("duration")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        val durationText = if (durationSec > 0) {
            val m = durationSec / 60
            val s = durationSec % 60
            "%d:%02d".format(m, s)
        } else ""
        val viewCount = stat?.get("view")?.takeUnless { it.isJsonNull }?.asLong ?: 0L
        val viewText = when {
            viewCount >= 100_000_000 -> String.format("%.1f亿", viewCount / 100_000_000.0)
            viewCount >= 10_000 -> String.format("%.1f万", viewCount / 10_000.0)
            viewCount > 0 -> viewCount.toString()
            else -> ""
        }
        val author = owner?.string("name")
            ?: owner?.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        return CoreVideo(
            id = id,
            bvid = bvid,
            title = title,
            cover = cover,
            author = author,
            cid = long("cid") ?: 0L,
            durationText = durationText,
            viewText = viewText,
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeUnless { it.isJsonNull }?.asLong

    private fun normalizeUrl(value: String?): String = when {
        value.isNullOrBlank() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        else -> value
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/122 Safari/537.36"
    }
}
