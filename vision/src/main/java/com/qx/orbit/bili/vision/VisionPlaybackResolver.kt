package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.PlaybackGateway
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun resolveVisionPlayback(gateway: PlaybackGateway, target: CoreVideo, quality: Int): CorePlayback {
    val resolved = gateway.resolveQuality(target, quality)
    coroutineContext.ensureActive()
    check(resolved.video.bvid == target.bvid && (target.cid == 0L || resolved.video.cid == target.cid)) { "播放地址与所选分 P 不匹配" }
    return resolved
}
