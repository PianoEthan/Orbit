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
        videoDuration: Long = 0,
        startTs: Long = System.currentTimeMillis() / 1000,
        realtime: Long = playedTime,
        type: String = "3",
        subType: String? = null,
        epid: Long? = null,
        sid: Long? = null
    ) = withContext(Dispatchers.IO) {
        val mid = CookieManager.getMid()
        val csrf = CookieManager.getCsrf()
        if (csrf.isBlank()) return@withContext
        try {
            val wbiParams = mapOf(
                "w_start_ts" to startTs.toString(),
                "w_mid" to mid.toString(),
                "w_aid" to aid.toString(),
                "w_dt" to "2",
                "w_realtime" to realtime.coerceAtLeast(0).toString(),
                "w_played_time" to playedTime.toString(),
                "w_real_played_time" to playedTime.toString(),
                "w_video_duration" to videoDuration.toString(),
                "w_last_play_progress_time" to playedTime.toString(),
                "web_location" to "1315873"
            )
            val signed = WbiSigner.signParams(wbiParams)
            val url = "https://api.bilibili.com/x/click-interface/web/heartbeat?" + signed.entries.joinToString("&") { "${it.key}=${it.value}" }
            
            api.reportHeartbeat(
                url = url,
                aid = aid, bvid = bvid, cid = cid, mid = mid, csrf = csrf, 
                playedTime = playedTime, realtime = realtime, startTs = startTs,
                type = type, subType = subType, epid = epid, sid = sid,
                videoDuration = videoDuration,
                lastPlayProgressTime = playedTime
            )
        } catch (_: Exception) {
        }
    }
}
