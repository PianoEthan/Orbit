package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import java.net.URLEncoder
import java.util.Locale
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class BilibiliSearchGateway(private val client: OkHttpClient = OkHttpClient()) : SearchGateway {
    override suspend fun searchVideos(keyword: String, page: Int): List<CoreVideo> {
        require(keyword.isNotBlank()) { "请输入搜索内容" }
        require(page > 0)
        val url = client.signedBilibiliUrl("https://api.bilibili.com/x/web-interface/wbi/search/type", mapOf(
            "keyword" to keyword.trim(), "page" to page.toString(), "search_type" to "video",
        ))
        val data = client.getBilibiliJson(url).requireBilibiliData()
        val result = data.get("result")?.takeUnless { it.isJsonNull }?.asJsonArray ?: return emptyList()
        return result.mapNotNull { entry ->
            val item = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val bvid = item.text("bvid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = Jsoup.parse(item.text("title")).text()
            val pic = item.text("pic")
            val cover = when {
                pic.startsWith("//") -> "https:$pic"
                pic.startsWith("http://") -> pic.replaceFirst("http://", "https://")
                else -> pic
            }
            CoreVideo(
                id = item.get("aid")?.takeUnless { it.isJsonNull }?.asLong ?: 0,
                bvid = bvid,
                title = title,
                cover = cover,
                author = item.text("author"),
                cid = item.text("cid").toLongOrNull() ?: 0L,
                durationText = item.text("duration").split(':').let { parts ->
                    if (parts.size in 2..3 && parts.all { it.toIntOrNull() != null }) {
                        parts.mapIndexed { index, part -> if (index == 0) part else part.padStart(2, '0') }.joinToString(":")
                    } else item.text("duration")
                },
                viewText = item.text("play").let { value ->
                    val count = value.toLongOrNull()
                    when {
                        count == null -> value
                        count >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", count / 100_000_000.0)
                        count >= 10_000 -> String.format(Locale.ROOT, "%.1f万", count / 10_000.0)
                        else -> count.toString()
                    }
                },
            )
        }.distinctBy { it.bvid }
    }

    override suspend fun searchUsers(keyword: String, page: Int): List<CoreSearchUser> =
        searchTypeResults(keyword, page, "bili_user").mapNotNull { entry ->
            val mid = entry.longValue("mid").takeIf { it > 0 } ?: return@mapNotNull null
            CoreSearchUser(
                mid = mid,
                name = entry.text("uname"),
                face = fixCover(entry.text("upic")),
                sign = Jsoup.parse(entry.text("usign")).text(),
                fans = entry.intValue("fans"),
            )
        }

    override suspend fun searchRooms(keyword: String, page: Int): List<CoreLiveRoom> =
        searchTypeResults(keyword, page, "live").mapNotNull { entry ->
            val roomId = entry.longValue("roomid").takeIf { it > 0 } ?: entry.longValue("short_id")
            if (roomId <= 0) return@mapNotNull null
            CoreLiveRoom(
                roomId = roomId,
                uid = entry.longValue("uid"),
                title = Jsoup.parse(entry.text("title")).text(),
                uname = entry.text("uname"),
                cover = fixCover(entry.text("user_cover").ifBlank { entry.text("cover") }),
                liveStatus = entry.intValue("live_status"),
                online = entry.intValue("online"),
            )
        }

    override suspend fun searchSeasons(keyword: String, page: Int): List<CoreSearchSeason> =
        searchTypeResults(keyword, page, "media_bangumi").mapNotNull { entry ->
            val seasonId = entry.longValue("season_id").takeIf { it > 0 }
                ?: entry.longValue("media_id").takeIf { it > 0 }
                ?: entry.longValue("id")
            if (seasonId <= 0) return@mapNotNull null
            CoreSearchSeason(
                seasonId = seasonId,
                title = Jsoup.parse(entry.text("title")).text(),
                cover = fixCover(entry.text("cover").ifBlank { entry.text("pic") }),
                subtitle = entry.text("index_show"),
                areas = entry.get("areas")?.let { areas ->
                    if (areas.isJsonPrimitive) areas.asString
                    else areas.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive }?.asString }
                        ?.joinToString("/")
                        .orEmpty()
                }.orEmpty(),
            )
        }

    private suspend fun searchTypeResults(keyword: String, page: Int, type: String): List<JsonObject> {
        require(keyword.isNotBlank()) { "请输入搜索内容" }
        require(page > 0)
        val url = client.signedBilibiliUrl("https://api.bilibili.com/x/web-interface/wbi/search/type", mapOf(
            "keyword" to keyword.trim(), "page" to page.toString(), "search_type" to type,
        ))
        val data = client.getBilibiliJson(url).requireBilibiliData()
        return data.arrayValue("result")
            ?.mapNotNull { entry -> entry.asJsonObjectOrNull() }
            .orEmpty()
    }

    private fun fixCover(url: String) = when {
        url.isBlank() -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> url.replaceFirst("http://", "https://")
        else -> url
    }

    override suspend fun loadHotwords(): List<CoreHotword> {
        val json = client.getBilibiliJson("https://s.search.bilibili.com/main/hotword")
        val list = json.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val keyword = obj.text("keyword").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val showName = obj.text("show_name").ifBlank { keyword }
            val icon = obj.text("icon")
            CoreHotword(keyword, showName, icon)
        }
    }

    override suspend fun loadSuggestions(keyword: String): List<String> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val json = client.getBilibiliJson("https://s.search.bilibili.com/main/suggest?term=$encoded")
        val result = json.getAsJsonObject("result") ?: return emptyList()
        val tag = result.getAsJsonArray("tag") ?: return emptyList()
        return tag.mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.text("value")?.takeIf { it.isNotBlank() }
        }.distinct()
    }
}
