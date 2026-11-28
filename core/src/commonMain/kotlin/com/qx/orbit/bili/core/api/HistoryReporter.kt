package com.qx.orbit.bili.core.api

fun interface HistoryReporter {
    suspend fun report(aid: Long, cid: Long, progressSeconds: Long, cookie: String, csrf: String)
    suspend fun reportEpisode(video: CoreVideo, progressSeconds: Long, cookie: String, csrf: String) = report(video.id, video.cid, progressSeconds, cookie, csrf)
}
