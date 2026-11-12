package com.qx.orbit.bili.core.api

import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

data class CoreSeason(val id: Long, val mediaId: Long, val title: String, val cover: String, val subtitle: String, val badge: String)
data class CoreSeasonPage(val items: List<CoreSeason>, val hasMore: Boolean)
data class CoreSeasonEpisode(val id: Long, val title: String, val badge: String, val video: CoreVideo)
data class CoreSeasonDetail(val season: CoreSeason, val description: String, val episodes: List<CoreSeasonEpisode>, val seasons: List<CoreSeason>,
    val followed: Boolean, val lastEpisodeId: Long, val lastPositionMs: Long)

interface SeasonGateway {
    suspend fun loadCatalog(type: Int, page: Int, cookie: String): CoreSeasonPage
    suspend fun loadFollowed(mid: Long, type: Int, page: Int, cookie: String): CoreSeasonPage
    suspend fun loadSeason(seasonId: Long, cookie: String): CoreSeasonDetail
    suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String)
}

class BilibiliSeasonGateway(private val client: OkHttpClient = OkHttpClient()) : SeasonGateway {
    override suspend fun loadCatalog(type: Int, page: Int, cookie: String): CoreSeasonPage {
        require(type in listOf(1, 2, 3, 4, 5, 7) && page > 0)
        val url = "https://api.bilibili.com/pgc/season/index/result?season_type=$type&type=1&order=3&sort=0&page=$page&pagesize=20"
        val data = client.getBilibiliJson(url, cookie).requireBilibiliData()
        return CoreSeasonPage(data.objects("list").mapNotNull { it.season() }, data.text("has_next") in listOf("1", "true"))
    }

    override suspend fun loadFollowed(mid: Long, type: Int, page: Int, cookie: String): CoreSeasonPage {
        require(mid > 0 && type in 1..2 && page > 0 && cookie.isNotBlank())
        val data = client.getBilibiliJson("https://api.bilibili.com/x/space/bangumi/follow/list?vmid=$mid&type=$type&pn=$page&ps=20", cookie).requireBilibiliData()
        val items = data.objects("list").mapNotNull { it.season() }
        return CoreSeasonPage(items, items.isNotEmpty() && page.toLong() * 20 < data.number("total"))
    }

    override suspend fun loadSeason(seasonId: Long, cookie: String): CoreSeasonDetail {
        require(seasonId > 0)
        val result = client.getBilibiliJson("https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId", cookie)
        check(result.number("code") == 0L && result.has("code")) { result.text("message").ifBlank { "剧集详情加载失败" } }
        val data = checkNotNull(result.obj("result") ?: result.obj("data")) { "剧集详情缺失" }
        val season = checkNotNull(data.season()) { "剧集信息缺失" }
        val status = data.obj("user_status")
        val progress = status?.obj("progress")
        val episodes = (data.objects("episodes") + data.objects("section").flatMap { it.objects("episodes") }).mapNotNull { item ->
            val epId = item.number("id")
            val aid = item.number("aid")
            val cid = item.number("cid")
            if (epId <= 0 || aid <= 0 || cid <= 0) return@mapNotNull null
            val bvid = item.text("bvid").ifBlank { "ep$epId" }
            val title = listOf(item.text("title"), item.text("long_title")).filter { it.isNotBlank() }.joinToString(" · ")
            CoreSeasonEpisode(epId, title, item.text("badge"), CoreVideo(aid, bvid, title, item.text("cover").secureMediaUrl(), season.title,
                cid = cid, episodeId = epId, seasonId = season.id,
                startPositionMs = if (epId == progress?.number("last_ep_id")) (progress.number("last_time") * 1000).coerceAtLeast(0) else 0))
        }.distinctBy { it.id }
        return CoreSeasonDetail(season, data.text("evaluate"), episodes, data.objects("seasons").mapNotNull { it.season() },
            status?.text("follow") in listOf("1", "true"), progress?.number("last_ep_id") ?: 0, (progress?.number("last_time") ?: 0).coerceAtLeast(0) * 1000)
    }

    override suspend fun follow(seasonId: Long, followed: Boolean, cookie: String, csrf: String) {
        require(seasonId > 0 && cookie.isNotBlank() && csrf.isNotBlank()) { "请先登录" }
        val result = client.postBilibiliForm("https://api.bilibili.com/pgc/web/follow/${if (followed) "add" else "del"}", cookie,
            mapOf("season_id" to "$seasonId", "csrf" to csrf))
        check(result.has("code") && result.number("code") == 0L) { result.text("message").ifBlank { "追剧操作结果未确认，请刷新" } }
    }

    private fun JsonObject.season(): CoreSeason? {
        val id = number("season_id").takeIf { it > 0 } ?: return null
        return CoreSeason(id, number("media_id"), text("title").ifBlank { text("season_title") }, text("cover").secureMediaUrl(),
            text("index_show").ifBlank { obj("new_ep")?.text("index_show").orEmpty() }, text("badge"))
    }
    private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.number(key: String) = text(key).toLongOrNull() ?: 0L
    private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.objects(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }.orEmpty()
}
