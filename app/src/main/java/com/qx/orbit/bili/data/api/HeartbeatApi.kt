package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

object HeartbeatApi {

    private val api by lazy { BiliApiService.create() }

    suspend fun reportHeartbeat(
        aid: Long,
        bvid: String,
        cid: Long,
        playedTime: Long,
        startTs: Long = System.currentTimeMillis() / 1000,
        realtime: Long = playedTime
    ) = withContext(Dispatchers.IO) {
        val mid = CookieManager.getMid()
        val csrf = CookieManager.getCsrf()
        if (csrf.isBlank()) return@withContext
        try {
            api.reportHeartbeat(aid, bvid, cid, mid, csrf, playedTime, realtime, startTs)
        } catch (_: Exception) {
        }
    }
}
