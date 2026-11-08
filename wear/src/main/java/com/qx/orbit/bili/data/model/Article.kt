package com.qx.orbit.bili.data.model

data class ArticleInfo(
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    val banner: String = "",
    val upInfo: UserInfo? = null,
    val ctime: Long = 0,
    val stats: Stats? = null,
    val wordCount: Int = 0,
    val keywords: String = "",
    val content: String = ""
)

data class ArticleCard(
    val title: String = "",
    val id: Long = 0,
    val cover: String = "",
    val upName: String = "",
    val view: String = ""
)

data class ArticleLine(
    val type: Int = 0,
    val content: String = "",
    val extra: String = ""
)
