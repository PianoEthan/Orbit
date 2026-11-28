package com.qx.orbit.bili.core.api

fun interface DanmakuGateway {
    suspend fun loadXmlDanmaku(cid: Long): ByteArray?
    suspend fun sendDanmaku(cid: Long, bvid: String, msg: String, progressMs: Long, color: Int, mode: Int, cookie: String, csrf: String): Boolean = false
}
