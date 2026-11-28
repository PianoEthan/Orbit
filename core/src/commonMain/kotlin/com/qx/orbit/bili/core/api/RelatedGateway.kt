package com.qx.orbit.bili.core.api

fun interface RelatedGateway {
    suspend fun loadRelated(bvid: String, cookie: String): List<CoreVideo>
}
