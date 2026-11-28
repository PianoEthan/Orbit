package com.qx.orbit.bili.core.api

data class CoreArticleStats(
    val view: Long = 0,
    val like: Long = 0,
    val reply: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
)

enum class CoreArticleTextKind { PARAGRAPH, HEADING, QUOTE }

sealed interface CoreArticleBlock {
    data class Text(val text: String, val kind: CoreArticleTextKind = CoreArticleTextKind.PARAGRAPH) : CoreArticleBlock
    data class Image(val url: String) : CoreArticleBlock
    data object Divider : CoreArticleBlock
}

data class CoreArticle(
    val id: String,
    val title: String,
    val author: String,
    val authorMid: Long,
    val avatar: String,
    val published: String,
    val banner: String,
    val stats: CoreArticleStats,
    val blocks: List<CoreArticleBlock>,
)

interface ArticleGateway {
    suspend fun loadArticle(cv: Long, cookie: String): CoreArticle
    suspend fun loadOpus(id: String, cookie: String): CoreArticle
    suspend fun likeArticle(cv: Long, like: Boolean, cookie: String, csrf: String): Boolean = false
    suspend fun favoriteArticle(cv: Long, add: Boolean, cookie: String, csrf: String): Boolean = false
    suspend fun coinArticle(cv: Long, upid: Long, multiply: Int, cookie: String, csrf: String): Boolean = false
}
