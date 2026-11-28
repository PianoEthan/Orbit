package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CorePlayback

internal fun playbackAddressGroups(playback: CorePlayback): List<List<String>> {
    val audioUrl = playback.audioUrl
    if (audioUrl != null) {
        return listOf(
            (listOf(playback.urls.first()) + playback.videoBackups).distinct(),
            (listOf(audioUrl) + playback.audioBackups).distinct(),
        )
    }
    return playback.urls.mapIndexed { index, url ->
        (listOf(url) + (playback.segmentBackups.getOrNull(index)
            ?: if (index == 0) playback.videoBackups else emptyList())).distinct()
    }
}
