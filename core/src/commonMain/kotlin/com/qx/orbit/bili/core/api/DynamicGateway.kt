package com.qx.orbit.bili.core.api

data class CoreDynamic(
    val id: String,
    val author: String,
    val avatar: String,
    val published: String,
    val text: String,
    val images: List<String>,
    val video: CoreVideo? = null,
    val original: CoreDynamic? = null,
    val unsupportedType: String? = null,
    val articleId: Long? = null,
    val opus: Boolean = false,
)

data class CoreDynamicPage(val items: List<CoreDynamic>, val nextOffset: String?)

fun interface DynamicGateway {
    suspend fun load(cookie: String, offset: String): CoreDynamicPage
    suspend fun loadDetail(id: String, cookie: String): CoreDynamic = error("暂不支持动态详情")
    suspend fun repost(dynamicId: String, text: String, cookie: String, csrf: String): Boolean = false
}
