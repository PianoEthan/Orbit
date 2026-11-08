package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class VisionMediaSourcesTest {
    private val video = CoreVideo(1, "BVtest", "标题", "", "UP")

    @Test fun dashRetainsBothTracksAndFirstBackup() {
        val playback = CorePlayback(video, listOf("https://cdn/video"), audioUrl = "https://cdn/audio",
            videoBackups = listOf("https://backup/video", "https://cdn/video"),
            audioBackups = listOf("https://backup/audio"))
        assertEquals(listOf(
            listOf("https://cdn/video", "https://backup/video"),
            listOf("https://cdn/audio", "https://backup/audio"),
        ), playbackAddressGroups(playback))
    }

    @Test fun progressiveKeepsAllSegmentsAndTheirOwnBackups() {
        val playback = CorePlayback(video, listOf("https://cdn/part1", "https://cdn/part2"),
            segmentBackups = listOf(listOf("https://backup/part1"), listOf("https://backup/part2")))
        assertEquals(listOf(
            listOf("https://cdn/part1", "https://backup/part1"),
            listOf("https://cdn/part2", "https://backup/part2"),
        ), playbackAddressGroups(playback))
    }
}
