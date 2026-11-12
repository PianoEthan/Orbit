package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.PlaybackGateway
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun resolveVisionPlayback(gateway: PlaybackGateway, target: CoreVideo, quality: Int, preferProgressive: Boolean = false): CorePlayback {
    val resolved = gateway.resolveStream(target, quality, preferProgressive)
    coroutineContext.ensureActive()
    check(resolved.video.bvid == target.bvid && (target.cid == 0L || resolved.video.cid == target.cid)
        && resolved.video.episodeId == target.episodeId && resolved.video.seasonId == target.seasonId) { "播放地址与所选分 P 或剧集不匹配" }
    return resolved
}
