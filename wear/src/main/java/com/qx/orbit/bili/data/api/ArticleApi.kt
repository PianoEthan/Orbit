package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.Result
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArticleApi {

    private val api by lazy { BiliApiService.create() }

    internal data class ArticleViewData(
        @SerializedName("id") val id: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("summary") val summary: String? = null,
        @SerializedName("banner_url") val banner_url: String? = null,
        @SerializedName("author") val author: AuthorData? = null,
        @SerializedName("ctime") val ctime: Long = 0,
        @SerializedName("stats") val stats: StatsData? = null,
        @SerializedName("words") val words: Int = 0,
        @SerializedName("dynamic") val dynamic: String? = null,
        @SerializedName("content") val content: String? = null,
        @SerializedName("opus") val opus: OpusData? = null
    )

    internal data class OpusData(
        @SerializedName("content") val content: OpusContentData? = null
    )

    internal data class OpusContentData(
        @SerializedName("paragraphs") val paragraphs: List<OpusApi.ParagraphData>? = null
    )

    internal data class AuthorData(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null
    )

    internal data class StatsData(
        @SerializedName("view") val view: Int = 0,
        @SerializedName("like") val like: Int = 0,
        @SerializedName("reply") val reply: Int = 0,
        @SerializedName("coin") val coin: Int = 0,
        @SerializedName("share") val share: Int = 0,
        @SerializedName("favorite") val favorite: Int = 0
    )

    suspend fun getArticle(id: Long): ArticleInfo? = withContext(Dispatchers.IO) {
        val jsonElement = when (val result = api.getArticle(id)) {
            is Result.Success -> result.data
            is Result.Error -> return@withContext null
        }
        android.util.Log.d("ArticleApi", "Raw JSON for article $id: $jsonElement")
        val typeToken = object : TypeToken<ApiResponse<ArticleViewData>>() {}.type
        val resp: ApiResponse<ArticleViewData>? = GsonConfig.gson.fromJson(jsonElement, typeToken)
        if (resp == null || !resp.isSuccess || resp.data == null) return@withContext null
        val data = resp.data
        val author = data.author
        val stats = data.stats
        var finalContent = data.content ?: ""
        val opusParas = data.opus?.content?.paragraphs
        if (opusParas != null && opusParas.isNotEmpty()) {
            finalContent = buildString {
                for (p in opusParas) {
                    when (p.para_type) {
                        1, 4 -> {
                            val text = p.text?.nodes?.joinToString("") { it.word?.words ?: it.rich?.text ?: "" } ?: ""
                            append("<p>$text</p>")
                        }
                        2 -> {
                            p.pic?.pics?.forEach { pic ->
                                append("<figure><img src=\"${pic.url}\" /></figure>")
                            }
                        }
                        3 -> append("<hr>")
                    }
                }
            }
        }

        ArticleInfo(
            id = data.id,
            title = data.title ?: "",
            summary = data.summary ?: "",
            banner = data.banner_url ?: "",
            upInfo = author?.let {
                UserInfo(mid = it.mid, name = it.name ?: "", avatar = it.face ?: "")
            },
            ctime = data.ctime,
            stats = stats?.let {
                Stats(
                    view = it.view, like = it.like, reply = it.reply,
                    coin = it.coin, share = it.share, favorite = it.favorite
                )
            },
            wordCount = data.words,
            keywords = data.dynamic ?: "",
            content = finalContent
        )
    }

    suspend fun like(cvid: Long, type: Int): Int = withContext(Dispatchers.IO) {
        when (val result = api.likeArticle(cvid, type, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }

    suspend fun addCoin(cvid: Long, upid: Long, multiply: Int = 1): Int = withContext(Dispatchers.IO) {
        when (val result = api.coinArticle(cvid, upid, multiply, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }

    suspend fun favorite(cvid: Long): Int = withContext(Dispatchers.IO) {
        when (val result = api.favoriteArticle(cvid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }

    suspend fun delFavorite(cvid: Long): Int = withContext(Dispatchers.IO) {
        when (val result = api.delFavoriteArticle(cvid, CookieManager.getCsrf())) {
            is Result.Success -> 0
            is Result.Error -> result.exception.code
        }
    }
}
