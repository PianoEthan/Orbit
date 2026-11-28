package com.qx.orbit.bili.vision

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qx.orbit.bili.core.api.BilibiliDanmakuGateway
import com.qx.orbit.bili.core.api.DanmakuGateway
import kotlinx.coroutines.CoroutineScope

internal expect class VisionDanmakuHost(gateway: DanmakuGateway = BilibiliDanmakuGateway()) {
    fun load(cid: Long, scope: CoroutineScope)
    fun synchronize(positionMs: Long, playing: Boolean, speed: Float)
    fun setPlaying(playing: Boolean)
    fun setEnabled(enabled: Boolean)
    fun updateConfig(alpha: Float, textScale: Float)
    fun release()
}

@Composable
internal expect fun VisionDanmakuLayer(host: VisionDanmakuHost, modifier: Modifier)
