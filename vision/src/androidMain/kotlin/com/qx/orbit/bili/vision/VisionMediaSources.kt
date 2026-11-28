package com.qx.orbit.bili.vision

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.qx.orbit.bili.core.api.CorePlayback

@OptIn(UnstableApi::class)
internal fun buildVisionMediaSources(playback: CorePlayback, factory: DataSource.Factory): List<MediaSource> {
    val sources = playbackAddressGroups(playback).map { addresses ->
        val candidates = addresses.map(Uri::parse)
        val fallback = DataSource.Factory { VisionFailoverDataSource(factory, candidates) }
        ProgressiveMediaSource.Factory(fallback).createMediaSource(MediaItem.fromUri(addresses.first()))
    }
    return if (playback.audioUrl != null) listOf(MergingMediaSource(*sources.toTypedArray())) else sources
}
