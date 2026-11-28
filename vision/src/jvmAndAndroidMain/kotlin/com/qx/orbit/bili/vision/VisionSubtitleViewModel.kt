package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliSubtitleGateway
import com.qx.orbit.bili.core.api.CoreSubtitleCue
import com.qx.orbit.bili.core.api.CoreSubtitleTrack
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.SubtitleGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisionSubtitleState(
    val tracks: List<CoreSubtitleTrack> = emptyList(), val selected: CoreSubtitleTrack? = null,
    val cues: List<CoreSubtitleCue> = emptyList(), val loading: Boolean = false, val error: String? = null,
)

class VisionSubtitleViewModel(private val gateway: SubtitleGateway = BilibiliSubtitleGateway()) : ViewModel() {
    private val _state = MutableStateFlow(VisionSubtitleState())
    val state = _state.asStateFlow()
    private var video: CoreVideo? = null
    private var cookie = ""
    private var generation = 0
    private var job: Job? = null
    fun open(video: CoreVideo, cookie: String) {
        if (this.video?.bvid == video.bvid && this.video?.cid == video.cid && this.cookie == cookie) return
        this.video = video
        this.cookie = cookie
        loadTracks()
    }
    fun loadTracks() {
        val target = video ?: return
        val account = cookie
        val token = ++generation
        job?.cancel()
        _state.value = VisionSubtitleState(loading = true)
        job = viewModelScope.launch {
            try {
                val tracks = gateway.loadTracks(target, account)
                if (token == generation) _state.value = VisionSubtitleState(tracks)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = VisionSubtitleState(error = e.message ?: "字幕列表加载失败")
            }
        }
    }
    fun select(track: CoreSubtitleTrack?) {
        if (track != null && track !in _state.value.tracks) return
        val token = ++generation
        job?.cancel()
        _state.value = _state.value.copy(selected = track, cues = emptyList(), loading = track != null, error = null)
        if (track == null) return
        val account = cookie
        job = viewModelScope.launch {
            try {
                val cues = gateway.loadCues(track, account)
                if (token == generation) _state.value = _state.value.copy(cues = cues, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (e: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "字幕加载失败")
            }
        }
    }
}

internal fun activeSubtitle(cues: List<CoreSubtitleCue>, positionMs: Long): String {
    val end = cues.indexOfLast { it.fromMs <= positionMs }
    if (end < 0) return ""
    return cues.take(end + 1).filter { positionMs < it.toMs }.joinToString("\n") { it.content }
}
